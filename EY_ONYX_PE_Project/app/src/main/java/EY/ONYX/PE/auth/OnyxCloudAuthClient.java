package EY.ONYX.PE.auth;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dalvik.system.InMemoryDexClassLoader;

/**
 * Cloud Auth & Memory-Only Module Loader.
 * Zero disk persistence and zero test/backdoor accounts.
 */
public final class OnyxCloudAuthClient {

    public static final String DEFAULT_SERVER_URL = "https://exe.p8.ink/onyx_auth_server.php";

    public interface AuthCallback {
        void onProgress(int percent);
        void onSuccess(String username, double balance);
        void onFailure(String errorMessage);
    }

    private static volatile String sessionId;
    private static volatile byte[] sessionKey256;
    private static volatile long expiresAtSec;
    private static volatile boolean authenticated = false;
    private static volatile String activeUsername = "";
    private static volatile double activeBalance = 0.0;
    private static volatile ClassLoader memoryDexClassLoader;

    private OnyxCloudAuthClient() {}

    public static boolean isAuthenticated() {
        if (!authenticated) return false;
        if (expiresAtSec > 0 && (System.currentTimeMillis() / 1000L) > expiresAtSec) {
            clearSession();
            return false;
        }
        return true;
    }

    public static String getActiveUsername() {
        return activeUsername;
    }

    public static double getActiveBalance() {
        return activeBalance;
    }

    public static long getRemainingSeconds() {
        if (expiresAtSec <= 0) return 0L;
        return Math.max(0L, expiresAtSec - (System.currentTimeMillis() / 1000L));
    }

    public static ClassLoader getMemoryDexClassLoader() {
        return memoryDexClassLoader;
    }

    public static synchronized void clearSession() {
        authenticated = false;
        activeUsername = "";
        activeBalance = 0.0;
        sessionId = null;
        expiresAtSec = 0L;
        memoryDexClassLoader = null;
        if (sessionKey256 != null) {
            Arrays.fill(sessionKey256, (byte) 0);
            sessionKey256 = null;
        }
    }

    public static void authenticateAndLoadInMemory(
            final Context ctx,
            final String serverUrl,
            final String username,
            final String password,
            final AuthCallback callback
    ) {
        new Thread(() -> {
            byte[] assembledPayload = null;
            try {
                clearSession();
                String cleanUser = username == null ? "" : username.trim();
                String cleanPass = password == null ? "" : password;
                if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
                    callback.onFailure("Please enter username and password");
                    return;
                }

                callback.onProgress(15);
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair kp = kpg.generateKeyPair();
                String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP)
                        + "\n-----END PUBLIC KEY-----";

                String deviceHash = computeDeviceHash(ctx);

                callback.onProgress(30);
                JSONObject hsReq = new JSONObject();
                hsReq.put("action", "handshake");
                hsReq.put("client_pub_pem", pubPem);
                hsReq.put("device_hash", deviceHash);
                JSONObject hsResp = postJson(serverUrl, hsReq);
                if (!hsResp.optBoolean("ok", false)) {
                    callback.onFailure(hsResp.optString("error", "Connection failed"));
                    return;
                }

                sessionId = hsResp.getString("session_id");
                long ttl = hsResp.optLong("ttl_seconds", 180L);
                expiresAtSec = (System.currentTimeMillis() / 1000L) + ttl;

                byte[] wrappedKey = Base64.decode(hsResp.getString("wrapped_key"), Base64.DEFAULT);
                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                rsaCipher.init(Cipher.DECRYPT_MODE, kp.getPrivate());
                sessionKey256 = rsaCipher.doFinal(wrappedKey);
                if (sessionKey256.length != 32) {
                    clearSession();
                    callback.onFailure("Connection failed");
                    return;
                }

                callback.onProgress(50);
                long ts = System.currentTimeMillis() / 1000L;
                String nonce = randomNonce();
                JSONObject credsJson = new JSONObject();
                credsJson.put("username", cleanUser);
                credsJson.put("password", cleanPass);

                String loginAad = sessionId + ":login:" + ts + ":" + nonce;
                JSONObject encCreds = aesGcmEncrypt(credsJson.toString().getBytes(StandardCharsets.UTF_8), sessionKey256, loginAad);

                JSONObject loginReq = new JSONObject();
                loginReq.put("action", "login");
                loginReq.put("session_id", sessionId);
                loginReq.put("ts", ts);
                loginReq.put("nonce", nonce);
                loginReq.put("hmac", hmacSha256Hex("login:" + sessionId + ":" + ts + ":" + nonce, sessionKey256));
                loginReq.put("iv", encCreds.getString("iv"));
                loginReq.put("tag", encCreds.getString("tag"));
                loginReq.put("data", encCreds.getString("data"));

                JSONObject loginResp = postJson(serverUrl, loginReq);
                if (!loginResp.optBoolean("ok", false)) {
                    clearSession();
                    callback.onFailure(loginResp.optString("error", "Invalid username or password"));
                    return;
                }

                JSONObject loginEnc = loginResp.getJSONObject("encrypted");
                byte[] loginMetaBytes = aesGcmDecrypt(loginEnc, sessionKey256, sessionId + ":login_ok");
                JSONObject loginMeta = new JSONObject(new String(loginMetaBytes, StandardCharsets.UTF_8));
                int totalChunks = loginMeta.getInt("total_chunks");
                String expectedSha256 = loginMeta.getString("payload_sha256");
                double balance = loginMeta.optDouble("balance", 0.0);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (int i = 0; i < totalChunks; i++) {
                    if (getRemainingSeconds() <= 0) {
                        clearSession();
                        callback.onFailure("Session expired");
                        return;
                    }
                    int pct = 55 + Math.round((40f * (i + 1)) / Math.max(1, totalChunks));
                    callback.onProgress(pct);

                    long cts = System.currentTimeMillis() / 1000L;
                    String cnonce = randomNonce();
                    JSONObject chunkReq = new JSONObject();
                    chunkReq.put("action", "download_chunk");
                    chunkReq.put("session_id", sessionId);
                    chunkReq.put("chunk_index", i);
                    chunkReq.put("ts", cts);
                    chunkReq.put("nonce", cnonce);
                    chunkReq.put("hmac", hmacSha256Hex("chunk:" + sessionId + ":" + i + ":" + cts + ":" + cnonce, sessionKey256));

                    JSONObject chunkResp = postJson(serverUrl, chunkReq);
                    if (!chunkResp.optBoolean("ok", false)) {
                        clearSession();
                        callback.onFailure(chunkResp.optString("error", "Verification failed"));
                        return;
                    }

                    JSONObject encChunk = chunkResp.getJSONObject("encrypted");
                    byte[] plainChunk = aesGcmDecrypt(encChunk, sessionKey256, sessionId + ":chunk:" + i + ":" + cts);
                    baos.write(plainChunk);
                    Arrays.fill(plainChunk, (byte) 0);
                }

                assembledPayload = baos.toByteArray();
                String actualSha256 = sha256Hex(assembledPayload);
                if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                    clearSession();
                    callback.onFailure("Verification failed");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && assembledPayload.length > 4
                        && assembledPayload[0] == 'd' && assembledPayload[1] == 'e' && assembledPayload[2] == 'x') {
                    ByteBuffer buf = ByteBuffer.wrap(assembledPayload);
                    memoryDexClassLoader = new InMemoryDexClassLoader(buf, ctx.getClassLoader());
                }

                authenticated = true;
                activeUsername = cleanUser;
                activeBalance = balance;
                callback.onProgress(100);
                callback.onSuccess(cleanUser, balance);
            } catch (Throwable t) {
                clearSession();
                callback.onFailure("Connection failed");
            } finally {
                if (assembledPayload != null) {
                    Arrays.fill(assembledPayload, (byte) 0);
                }
            }
        }, "onyx-auth").start();
    }

    private static JSONObject postJson(String urlStr, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
            is.close();
        }
        return new JSONObject(baos.toString("UTF-8"));
    }

    private static JSONObject aesGcmEncrypt(byte[] plain, byte[] key256, String aad) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key256, "AES"), new GCMParameterSpec(128, iv));
        c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] out = c.doFinal(plain);
        int ctLen = out.length - 16;
        byte[] ct = Arrays.copyOfRange(out, 0, ctLen);
        byte[] tag = Arrays.copyOfRange(out, ctLen, out.length);
        JSONObject obj = new JSONObject();
        obj.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        obj.put("tag", Base64.encodeToString(tag, Base64.NO_WRAP));
        obj.put("data", Base64.encodeToString(ct, Base64.NO_WRAP));
        return obj;
    }

    private static byte[] aesGcmDecrypt(JSONObject enc, byte[] key256, String aad) throws Exception {
        byte[] iv = Base64.decode(enc.getString("iv"), Base64.DEFAULT);
        byte[] tag = Base64.decode(enc.getString("tag"), Base64.DEFAULT);
        byte[] ct = Base64.decode(enc.getString("data"), Base64.DEFAULT);
        byte[] combined = new byte[ct.length + tag.length];
        System.arraycopy(ct, 0, combined, 0, ct.length);
        System.arraycopy(tag, 0, combined, ct.length, tag.length);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key256, "AES"), new GCMParameterSpec(128, iv));
        c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return c.doFinal(combined);
    }

    private static String hmacSha256Hex(String msg, byte[] key256) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key256, "HmacSHA256"));
        return toHex(mac.doFinal(msg.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return toHex(md.digest(data));
    }

    private static String computeDeviceHash(Context ctx) {
        try {
            String aid = ctx != null ? Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID) : "unknown";
            String raw = aid + "|" + Build.MODEL + "|" + Build.BOARD;
            return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return "unknown_device";
        }
    }

    private static String randomNonce() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        return toHex(b);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte v : bytes) {
            sb.append(Character.forDigit((v >> 4) & 0xF, 16));
            sb.append(Character.forDigit(v & 0xF, 16));
        }
        return sb.toString();
    }
}
