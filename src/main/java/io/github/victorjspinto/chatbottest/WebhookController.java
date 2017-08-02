package io.github.victorjspinto.chatbottest;

import static com.github.messenger4j.MessengerPlatform.CHALLENGE_REQUEST_PARAM_NAME;
import static com.github.messenger4j.MessengerPlatform.MODE_REQUEST_PARAM_NAME;
import static com.github.messenger4j.MessengerPlatform.SIGNATURE_HEADER_NAME;
import static com.github.messenger4j.MessengerPlatform.VERIFY_TOKEN_REQUEST_PARAM_NAME;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.messenger4j.MessengerPlatform;
import com.github.messenger4j.exceptions.MessengerApiException;
import com.github.messenger4j.exceptions.MessengerIOException;
import com.github.messenger4j.exceptions.MessengerVerificationException;
import com.github.messenger4j.receive.MessengerReceiveClient;
import com.github.messenger4j.receive.handlers.TextMessageEventHandler;
import com.github.messenger4j.send.MessengerSendClient;

@RestController
@RequestMapping("/callback")
public class WebhookController {

	private static final Logger LOGGER = LoggerFactory.getLogger(WebhookController.class);

	private final MessengerReceiveClient receiveClient;
	private final MessengerSendClient sendClient;

	public WebhookController(@Value("${messenger4j.appSecret}") final String appSecret,
			@Value("${messenger4j.verifyToken}") final String verifyToken,
			final MessengerSendClient sendClient) {

		this.receiveClient = MessengerPlatform.newReceiveClientBuilder(appSecret, verifyToken)
				.onTextMessageEvent(textMessageEventHandler())
				.build();

		this.sendClient = sendClient;
	}

	@GetMapping
	public ResponseEntity<String> checkToken(
			@RequestParam(value = CHALLENGE_REQUEST_PARAM_NAME, required = true) String challenge,
			@RequestParam(value = MODE_REQUEST_PARAM_NAME, required = true) String mode,
			@RequestParam(value = VERIFY_TOKEN_REQUEST_PARAM_NAME, required = true) String token) {

		if (!token.equals("batatinha")) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Wrong verification token");
		}

		return ResponseEntity.ok(challenge);
	}

	@PostMapping
	public ResponseEntity<Void> receiveMessage(@RequestBody final String payload,
			@RequestHeader(SIGNATURE_HEADER_NAME) final String signature) {

		LOGGER.debug("Received Messenger Platform callback - payload: {} | signature: {}", payload, signature);

		try {
            this.receiveClient.processCallbackPayload(payload, signature);
            LOGGER.info("Processed callback payload successfully");
            return ResponseEntity.ok().build();
        } catch (MessengerVerificationException e) {
        	LOGGER.warn("Processing of callback payload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }	
	}
	
	
	
	
	
	private TextMessageEventHandler textMessageEventHandler() {
		LOGGER.info("");
		return (event) -> {
			LOGGER.info("Received message '{}' with text '{}' from user '{}' at '{}'", event.getMid(), event.getText(),
					event.getSender().getId(), event.getTimestamp());
			try {
				sendClient.sendTextMessage(event.getSender().getId(), "Olá!");
			} catch (MessengerApiException | MessengerIOException e) {
				handleSendException(e);
			}
		};
	}
	
    private void handleSendException(Exception e) {
    	LOGGER.error("Message could not be sent. An unexpected error occurred.", e);
    }

}
