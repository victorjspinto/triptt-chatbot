package io.github.victorjspinto.chatbottest;

import static com.github.messenger4j.MessengerPlatform.CHALLENGE_REQUEST_PARAM_NAME;
import static com.github.messenger4j.MessengerPlatform.MODE_REQUEST_PARAM_NAME;
import static com.github.messenger4j.MessengerPlatform.SIGNATURE_HEADER_NAME;
import static com.github.messenger4j.MessengerPlatform.VERIFY_TOKEN_REQUEST_PARAM_NAME;

import java.util.List;

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
import com.github.messenger4j.receive.handlers.FallbackEventHandler;
import com.github.messenger4j.receive.handlers.PostbackEventHandler;
import com.github.messenger4j.receive.handlers.QuickReplyMessageEventHandler;
import com.github.messenger4j.receive.handlers.TextMessageEventHandler;
import com.github.messenger4j.send.MessengerSendClient;
import com.github.messenger4j.send.QuickReply;
import com.github.messenger4j.send.buttons.Button;
import com.github.messenger4j.send.templates.ButtonTemplate;
import com.github.messenger4j.send.templates.GenericTemplate;
import com.github.messenger4j.send.templates.ReceiptTemplate;

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
				.onQuickReplyMessageEvent(quickReplyMessageEventHandler())
				.onPostbackEvent(postbackEventHandler())
				.fallbackEventHandler(fallbackEventHandler())
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
	

	private FallbackEventHandler fallbackEventHandler() {
		return event -> {
            LOGGER.debug("Received FallbackEvent: {}", event);

            final String senderId = event.getSender().getId();
            LOGGER.info("Received unsupported message from user '{}'", senderId);
        };
	}
	
	private TextMessageEventHandler textMessageEventHandler() {
		return (event) -> {
			LOGGER.info("Received message '{}' with text '{}' from user '{}' at '{}'", event.getMid(), event.getText(),
					event.getSender().getId(), event.getTimestamp());
			if (event.getText().toLowerCase().equals("recibo")) {
				sendReceiptTemplate(event.getSender().getId());
			} else {
				sendStepOneResponse(event.getSender().getId());
			}
		};
	}
	
	private void sendReceiptTemplate(String recipientId) {
		final String uniqueReceiptId = "order-" + Math.floor(Math.random() * 1000);

        final ReceiptTemplate receiptTemplate = ReceiptTemplate.newBuilder("Peter Chang", uniqueReceiptId, "USD", "Visa 1234")
                .timestamp(1428444852L)
                .addElements()
                    .addElement("Oculus Rift", 599.00f)
                        .subtitle("Includes: headset, sensor, remote")
                        .quantity(1)
                        .currency("USD")
                        .imageUrl("http://img.olx.com.br/images/79/798701037760443.jpg")
                        .toList()
                    .addElement("Samsung Gear VR", 99.99f)
                        .subtitle("Frost White")
                        .quantity(1)
                        .currency("USD")
                        .imageUrl("http://img.olx.com.br/images/79/798701037760443.jpg")
                        .toList()
                    .done()
                .addAddress("1 Hacker Way", "Menlo Park", "94025", "CA", "US").done()
                .addSummary(626.66f)
                    .subtotal(698.99f)
                    .shippingCost(20.00f)
                    .totalTax(57.67f)
                    .done()
                .addAdjustments()
                    .addAdjustment().name("New Customer Discount").amount(-50f).toList()
                    .addAdjustment().name("$100 Off Coupon").amount(-100f).toList()
                    .done()
                .build();

        try {
        	this.sendClient.sendTemplate(recipientId, receiptTemplate);
        } catch(Exception e) {
        	handleSendException(e);
        }
	}

	private PostbackEventHandler postbackEventHandler() {
		return (event) -> {
			String payload = event.getPayload();
			String senderId = event.getSender().getId();
			LOGGER.info("Received postback for user '{}' and page '{}' with payload '{}' at '{}'",
					senderId, event.getRecipient().getId(),
					payload, event.getTimestamp());
			
			actions(payload, senderId);
		};
	}

	private void actions(String payload, String senderId) {
		if(payload.equals("SEARCH")) {
			sendStepOneResponse(senderId);
		} else if (payload.startsWith("TRIP_FOR")) {
			sendStepTwoResponse(senderId);
		} else if(payload.startsWith("BUDGET")) {
			sendStepThreeResponse(senderId);
		} else if (payload.startsWith("TOURISM")) {
			sendStepFourResponse(senderId);
		} else if (payload.startsWith("SEASON")) {
			sendStepFiveResponse(senderId);
		}
	}
	
	private QuickReplyMessageEventHandler quickReplyMessageEventHandler() {
		return (event) -> {
			String payload = event.getQuickReply().getPayload();
			String senderId = event.getSender().getId();
			LOGGER.info("Received quick reply for user '{}' and page '{}' with payload '{}' at '{}'",
					senderId, event.getRecipient().getId(),
					payload, event.getTimestamp());
			
			actions(payload, senderId);
		};
	}

	private void sendStepFiveResponse(String senderId) {
		List<Button> buttons = Button.newListBuilder()
				.addUrlButton("Ver detalhes", "https://www.tripadvisor.com.br/Vacation_Packages-g303492-Armacao_dos_Buzios_State_of_Rio_de_Janeiro-Vacations.html").toList()
		        .build();
		
		 GenericTemplate receipt = GenericTemplate.newBuilder()
			.addElements()
	            .addElement("Armação de búzios")
	                .imageUrl("https://upload.wikimedia.org/wikipedia/commons/b/b9/Buzios_11_2006_03.JPG")
	                .subtitle("7 dias\nR$1.200")
	                .buttons(buttons)
	                .toList()
                .addElement("Armação de búzios")
	                .imageUrl("https://upload.wikimedia.org/wikipedia/commons/b/b9/Buzios_11_2006_03.JPG")
	                .subtitle("7 dias\nR$1.200")
	                .buttons(buttons)
	                .toList()
                .addElement("Armação de búzios")
	                .imageUrl("https://upload.wikimedia.org/wikipedia/commons/b/b9/Buzios_11_2006_03.JPG")
	                .subtitle("7 dias\nR$1.200")
	                .buttons(buttons)
	                .toList()
	        .done()
	        .build();
		
		try {
			sendClient.sendTextMessage(senderId, "Perai! Estamos montando alguns pacotes para você");
			sendClient.sendTemplate(senderId, receipt);
		} catch (MessengerApiException | MessengerIOException e) {
			handleSendException(e);
		}

	}

	private void sendStepFourResponse(String senderId) {
    	List<QuickReply> quickreplies = QuickReply.newListBuilder()
			.addTextQuickReply("Verão", "SEASON_SUMMER").toList()
        	.addTextQuickReply("Inverno", "SEASON_WINTER").toList()
        	.addTextQuickReply("Primavera", "SEASON_SPRING").toList()
        	.addTextQuickReply("Outono", "SEASON_AUTUMN").toList()
        	.build();
    	try {
			String message = "Qual estação do ano você mais gosta?";
			sendClient.sendTextMessage(senderId, message, quickreplies);
		} catch (MessengerApiException | MessengerIOException e) {
			handleSendException(e);
		}
	}


	private void sendStepThreeResponse(String senderId) {
    	List<QuickReply> quickreplies = QuickReply.newListBuilder()
        	.addTextQuickReply("Turista clássico", "TOURISM_CLASSIC").toList()
        	.addTextQuickReply("Turista aventureiro", "TOURISM_ADVENTURE").toList()
        	.addTextQuickReply("Turista ecológico", "TOURISM_ECO").toList()
        	.addTextQuickReply("Turista cult", "TOURISM_CULT").toList()
        	.addTextQuickReply("Turista nutella", "TOURISM_NUTELLA").toList()
	        .build();
    	try {
			String message = "Em uma viagem, qual tipo de perfil mais se encaixa com você?";
			sendClient.sendTextMessage(senderId, message, quickreplies);
		} catch (MessengerApiException | MessengerIOException e) {
			handleSendException(e);
		}
	}

	private void sendStepTwoResponse(String senderId) {
    	List<QuickReply> quickreplies = QuickReply.newListBuilder()
	        .addTextQuickReply("R$1.000 a R$5.000", "BUDGET_1K").toList()
	        .addTextQuickReply("R$5.000 a R$10.000", "BUDGET_5K").toList()
	        .addTextQuickReply("R$10.000 a R$15.000", "BUDGET_10K").toList()
	        .build();
    	try {
			String message = "Primeiro, quanto você tem disponível para viajar?";
			sendClient.sendTextMessage(senderId, message, quickreplies);
		} catch (MessengerApiException | MessengerIOException e) {
			handleSendException(e);
		}
	}

	private void sendStepOneResponse(String senderId) {
		List<Button> buttons = Button.newListBuilder()
				.addPostbackButton("Lazer", "TRIP_FOR_FUN").toList()
				.addPostbackButton("Trabalho", "TRIP_FOR_WORK").toList()
		        .build();
		
    	try {
    		String message = "Olá! Sou o Triptt Beta, vou te ajudar a viajar! Por enquanto, só posso te ajudar a viajar pelo Brasil! Para poder sugerir uma viagem, você precisa responder algumas coisas! Que tipo de viagem você quer fazer?";
            final ButtonTemplate buttonTemplate = ButtonTemplate.newBuilder(message, buttons).build();
            this.sendClient.sendTemplate(senderId, buttonTemplate);
		} catch (MessengerApiException | MessengerIOException e) {
			handleSendException(e);
		}
	}

	private void handleSendException(Exception e) {
    	LOGGER.error("Message could not be sent. An unexpected error occurred.", e);
    }

}
