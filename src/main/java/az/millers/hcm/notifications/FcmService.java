package az.millers.hcm.notifications;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import jakarta.annotation.PostConstruct;

/**
 * Thin wrapper around Firebase Admin SDK for sending FCM push notifications
 * (PRD §17.5).
 *
 * <p>FCM is entirely optional: if {@code HCM_FCM_CREDENTIALS_JSON} is not set
 * the service initialises in a no-op mode and {@link #send} returns
 * {@code false} without throwing. This keeps the app fully functional for
 * teams that only use email / in-app channels.
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    @Value("${hcm.fcm.credentials-json:}")
    private String credentialsJson;

    private FirebaseApp firebaseApp;

    @PostConstruct
    void init() {
        if (credentialsJson == null || credentialsJson.isBlank()) {
            log.warn("FCM not configured (hcm.fcm.credentials-json is empty). Push notifications disabled.");
            return;
        }
        try {
            GoogleCredentials creds = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped("https://www.googleapis.com/auth/firebase.messaging");
            FirebaseOptions opts = FirebaseOptions.builder().setCredentials(creds).build();
            if (FirebaseApp.getApps().isEmpty()) {
                firebaseApp = FirebaseApp.initializeApp(opts);
            } else {
                firebaseApp = FirebaseApp.getInstance();
            }
            log.info("FCM initialized successfully.");
        } catch (Exception e) {
            log.error("FCM init failed — push notifications will be disabled: {}", e.getMessage());
        }
    }

    /**
     * Send a push notification to a list of FCM tokens.
     *
     * @return {@code true} if at least one send succeeded; {@code false} when
     *         FCM is not configured or all sends failed.
     */
    public boolean send(List<String> tokens, String title, String body, Map<String, String> data) {
        if (firebaseApp == null || tokens == null || tokens.isEmpty()) {
            return false;
        }
        boolean anySuccess = false;
        Map<String, String> safeData = data != null ? data : Map.of();
        for (String token : tokens) {
            try {
                Message msg = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putAllData(safeData)
                        .build();
                FirebaseMessaging.getInstance(firebaseApp).send(msg);
                anySuccess = true;
            } catch (FirebaseMessagingException e) {
                String shortToken = token.substring(0, Math.min(20, token.length()));
                log.warn("FCM send failed for token {}...: {}", shortToken, e.getMessage());
            }
        }
        return anySuccess;
    }
}
