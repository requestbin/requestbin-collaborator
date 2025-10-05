package interactsh;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;
import com.github.shamil.Xid;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.util.DebugLogger;

public class InteractshClient {
	private PrivateKey privateKey;
	private PublicKey publicKey;

	private final String correlationId;
	private final String secretKey;
	private final String pubKeyBase64;

	private String host;
	private int port;
	private boolean scheme;
	private boolean registered;
	private String authorization;
	
	public String getCorrelationId() {
		return correlationId;
	}
	
	public boolean isRegistered() {
		return registered;
	}

	public InteractshClient() {
		this.correlationId = Xid.get().toString();
		this.secretKey = UUID.randomUUID().toString();

		KeyPair kp = generateKeys();
		this.publicKey = kp.getPublic();
		this.privateKey = kp.getPrivate();
		this.pubKeyBase64 =
				Base64.getEncoder().encodeToString(getPublicKey().getBytes(StandardCharsets.UTF_8));

		this.host = burp.gui.Config.getHost();
		this.scheme = burp.gui.Config.getScheme();
		this.authorization = burp.gui.Config.getAuth();
		try {
			this.port = Integer.parseInt(burp.gui.Config.getPort());
		} catch (NumberFormatException ne) {
			this.port = 443;
		}
	}

	public boolean register() {
		burp.BurpExtender.api.logging()
				.logToOutput("[InteractshClient] Starting registration process with correlation ID: " + correlationId);
		burp.BurpExtender.api.logging()
				.logToOutput("[InteractshClient] Registration details - Host: " + host + ", Port: " + port + 
				           ", Scheme: " + (scheme ? "HTTPS" : "HTTP") + ", HasAuth: " + (authorization != null && !authorization.isEmpty()));
		try {
			JSONObject registerData = new JSONObject();
			registerData.put("public-key", pubKeyBase64);
			registerData.put("secret-key", secretKey);
			registerData.put("correlation-id", correlationId);

			String requestBody = registerData.toString();
			burp.BurpExtender.api.logging()
					.logToOutput("[InteractshClient] Registration payload created - Length: " + requestBody.length() + 
					           ", PubKey length: " + pubKeyBase64.length());
			
			StringBuilder requestBuilder = new StringBuilder();

			requestBuilder.append("POST /register HTTP/1.1\r\n").append("Host: ").append(host)
					.append("\r\n").append("User-Agent: Interact.sh Client\r\n")
					.append("Content-Type: application/json\r\n").append("Content-Length: ")
					.append(requestBody.length()).append("\r\n");

			if (authorization != null && !authorization.isEmpty()) {
				requestBuilder.append("Authorization: ").append(authorization).append("\r\n");
			}

			requestBuilder.append("Connection: close\r\n\r\n").append(requestBody);

			String request = requestBuilder.toString();

			HttpService httpService = HttpService.httpService(host, port, scheme);
			HttpRequest httpRequest = HttpRequest.httpRequest(httpService, request);
			
			burp.BurpExtender.api.logging()
					.logToOutput("[InteractshClient] Sending registration request to " + host + ":" + port + "...");
			
			long startTime = System.currentTimeMillis();
			HttpResponse resp = burp.BurpExtender.api.http().sendRequest(httpRequest).response();
			long responseTime = System.currentTimeMillis() - startTime;
			
			burp.BurpExtender.api.logging()
					.logToOutput("[InteractshClient] Registration response received in " + responseTime + "ms - Status: " + resp.statusCode());

			if (resp.statusCode() == 200) {
				this.registered = true;
				burp.BurpExtender.api.logging().logToOutput("[InteractshClient] Session registration was successful for correlation: " + correlationId);
				burp.BurpExtender.api.logging().logToOutput("[InteractshClient] Generated interact domain: " + getInteractDomain());
				return true;
			} else {
				burp.BurpExtender.api.logging().logToError(
						"[InteractshClient] Registration was unsuccessful for correlation " + correlationId + 
						" - Status Code: " + resp.statusCode());
				String responseBody = resp.bodyToString();
				burp.BurpExtender.api.logging()
						.logToError("[InteractshClient] Error response body: " + responseBody);
			}
		} catch (Exception ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("UnknownHostException")) {
				burp.BurpExtender.api.logging().logToError(
						"[InteractshClient] Registration failed - the host '" + host + "' could not be resolved.");
			} else {
				burp.BurpExtender.api.logging().logToError(
						"[InteractshClient] Registration exception for correlation " + correlationId + ": " + 
						ex.getClass().getSimpleName() + " - " + ex.getMessage());
			}
		}
		burp.BurpExtender.api.logging().logToError(
				"[InteractshClient] Registration failed for correlation: " + correlationId);
		return false;
	}

	public boolean poll() {
		DebugLogger.debug("[InteractshClient] Starting poll for correlation: %s", correlationId);
		
		StringBuilder requestBuilder = new StringBuilder();

		requestBuilder.append("GET /poll?id=").append(correlationId).append("&secret=")
				.append(secretKey).append(" HTTP/1.1\r\n").append("Host: ").append(host)
				.append("\r\n").append("User-Agent: Interact.sh Client\r\n");
				
		DebugLogger.debug("[InteractshClient] Poll URL: /poll?id=%s&secret=[REDACTED]", correlationId);

		if (authorization != null && !authorization.isEmpty()) {
			requestBuilder.append("Authorization: ").append(authorization).append("\r\n");
		}

		requestBuilder.append("Connection: close\r\n\r\n");

		String request = requestBuilder.toString();

		HttpService httpService = HttpService.httpService(host, port, scheme);
		HttpRequest httpRequest = HttpRequest.httpRequest(httpService, request);
		HttpResponse resp = burp.BurpExtender.api.http().sendRequest(httpRequest).response();
		if (resp.statusCode() != 200) {
			burp.BurpExtender.api.logging().logToError("Session with correlation ID "
					+ correlationId + " was unsuccessful - status returned: " + resp.statusCode());
			return false;
		}

		String responseBody = resp.bodyToString();
		DebugLogger.debug("Received response body length: %d", responseBody.length());
		DebugLogger.debug("Response body preview: %s", responseBody.substring(0, Math.min(300, responseBody.length())) + (responseBody.length() > 300 ? "..." : ""));
		
		try {
			JSONObject jsonObject = new JSONObject(responseBody);
			DebugLogger.debug("Response JSON parsed successfully, keys: %s", String.join(", ", jsonObject.keySet()));
			
			String aesKey = jsonObject.getString("aes_key");
			DebugLogger.debug("AES key extracted, length: %d", aesKey.length());
			
			String key = this.decryptAesKey(aesKey);

			if (!jsonObject.isNull("data")) {
				JSONArray data = jsonObject.getJSONArray("data");
				DebugLogger.debug("Received data array with %d items", data.length());
				
				for (int i = 0; i < data.length(); i++) {
					String encryptedData = data.getString(i);
					DebugLogger.debug("Processing item %d/%d, encrypted length: %d", i+1, data.length(), encryptedData.length());
					
					String decryptedData = decryptData(encryptedData, key);
					DebugLogger.debug("Decrypted data length: %d", decryptedData.length());
					DebugLogger.debug("Decrypted data preview: %s", decryptedData.substring(0, Math.min(500, decryptedData.length())) + (decryptedData.length() > 500 ? "..." : ""));

					InteractshEntry entry = new InteractshEntry(decryptedData);
					burp.BurpExtender.addToTable(entry);
					DebugLogger.debug("Entry added to table successfully");
				}
			}
		} catch (Exception ex) {
			DebugLogger.debug("Exception in polling: %s - %s", ex.getClass().getSimpleName(), ex.getMessage());
			if (DebugLogger.isDebugMode()) {
				ex.printStackTrace();
			}
			
			if (ex.getMessage() != null && ex.getMessage().contains("UnknownHostException")) {
				DebugLogger.error("Polling failed - the host '" + host + "' could not be resolved.");
			} else {
				DebugLogger.error("Polling error: " + ex.getMessage());
				if (ex.getCause() != null) {
					DebugLogger.error("Caused by: " + ex.getCause().getMessage());
				}
			}
		}
		return true;
	}

	public void deregister() {
		burp.BurpExtender.api.logging()
				.logToOutput("[InteractshClient] Starting deregistration process for correlation ID: " + correlationId);
		try {
			JSONObject deregisterData = new JSONObject();
			deregisterData.put("correlation-id", correlationId);
			deregisterData.put("secret-key", secretKey);
			String requestBody = deregisterData.toString();
			
			burp.BurpExtender.api.logging()
					.logToOutput("[InteractshClient] Deregistration payload created for correlation: " + correlationId);

			StringBuilder requestBuilder = new StringBuilder();

			requestBuilder.append("POST /deregister HTTP/1.1\r\n").append("Host: ").append(host)
					.append("\r\nUser-Agent: Interact.sh Client\r\n")
					.append("Content-Type: application/json\r\n").append("Content-Length: ")
					.append(requestBody.length()).append("\r\n");

			if (authorization != null && !authorization.isEmpty()) {
				requestBuilder.append("Authorization: ").append(authorization).append("\r\n");
			}

			requestBuilder.append("Connection: close\r\n\r\n").append(requestBody);

			String request = requestBuilder.toString();

			HttpService httpService = HttpService.httpService(host, port, scheme);
			HttpRequest httpRequest = HttpRequest.httpRequest(httpService, request);
			
			burp.BurpExtender.api.logging()
					.logToOutput("[InteractshClient] Sending deregistration request for correlation: " + correlationId);
			
			HttpResponse deregResponse = burp.BurpExtender.api.http().sendRequest(httpRequest).response();
			
			burp.BurpExtender.api.logging()
					.logToOutput("[InteractshClient] Deregistration completed for correlation " + correlationId + 
					           " - Status: " + deregResponse.statusCode());
		} catch (Exception ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("UnknownHostException")) {
				burp.BurpExtender.api.logging().logToError(
						"[InteractshClient] Deregister failed - the host '" + host + "' could not be resolved for correlation: " + correlationId);
			} else {
				burp.BurpExtender.api.logging().logToError(
						"[InteractshClient] Deregister exception for correlation " + correlationId + ": " + 
						ex.getClass().getSimpleName() + " - " + ex.getMessage());
			}
		}
	}

	public String getInteractDomain() {
		if (correlationId == null || correlationId.isEmpty()) {
			return "";
		} else {
			String fullDomain = correlationId;

			// Fix the string up to 33 characters
			Random random = new Random();
			while (fullDomain.length() < 33) {
				fullDomain += (char) (random.nextInt(26) + 'a');
			}

			fullDomain += "." + host;
			return fullDomain;
		}
	}

	private KeyPair generateKeys() {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			return kpg.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			burp.BurpExtender.api.logging().logToError("Unable to generate client key pair", e);
			throw new RuntimeException(e);
		}
	}

	private String getPublicKey() {
		String pubKey = "-----BEGIN PUBLIC KEY-----\n";
		String[] chunks =
				splitStringEveryN(Base64.getEncoder().encodeToString(publicKey.getEncoded()), 64);
		for (String chunk : chunks) {
			pubKey += chunk + "\n";
		}
		pubKey += "-----END PUBLIC KEY-----\n";
		return pubKey;
	}

	private String decryptAesKey(String encrypted) throws Exception {
		DebugLogger.debug("Decrypting AES key - Encrypted length: %d", encrypted.length());
		
		byte[] cipherTextArray = Base64.getDecoder().decode(encrypted);
		DebugLogger.debug("AES key Base64 decoded - CipherText length: %d", cipherTextArray.length);

		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
		OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1",
				new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
		cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
		byte[] decrypted = cipher.doFinal(cipherTextArray);

		String result = new String(decrypted);
		DebugLogger.debug("AES key decryption successful - Key length: %d", result.length());
		return result;
	}

	private static String decryptData(String input, String key) throws Exception {
		DebugLogger.debug("Decrypting data - Input length: %d, Key length: %d", input.length(), key.length());
		
		byte[] cipherTextArray = Base64.getDecoder().decode(input);
		DebugLogger.debug("Base64 decoded - CipherText length: %d", cipherTextArray.length);
		
		byte[] iv = Arrays.copyOfRange(cipherTextArray, 0, 16);
		byte[] cipherText = Arrays.copyOfRange(cipherTextArray, 16, cipherTextArray.length - 1);
		DebugLogger.debug("IV length: %d, CipherText length: %d", iv.length, cipherText.length);

		IvParameterSpec ivSpec = new IvParameterSpec(iv);

		SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(), "AES");
		Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
		byte[] decrypted = cipher.doFinal(cipherText);
		
		String result = new String(decrypted);
		DebugLogger.debug("Decryption successful - Result length: %d", result.length());
		return result;
	}

	private String[] splitStringEveryN(String s, int interval) {
		int arrayLength = (int) Math.ceil(((s.length() / (double) interval)));
		String[] result = new String[arrayLength];

		int j = 0;
		int lastIndex = result.length - 1;
		for (int i = 0; i < lastIndex; i++) {
			result[i] = s.substring(j, j + interval);
			j += interval;
		}
		result[lastIndex] = s.substring(j);

		return result;
	}
}
