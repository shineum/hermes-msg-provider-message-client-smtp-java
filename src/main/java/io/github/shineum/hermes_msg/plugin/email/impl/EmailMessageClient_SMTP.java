package io.github.shineum.hermes_msg.plugin.email.impl;

import io.github.shineum.hermes_msg.IMessageClient;
import io.github.shineum.hermes_msg.entity.MessageResult;
import io.github.shineum.hermes_msg.plugin.email.entity.ByteArrayAttachment;
import io.github.shineum.hermes_msg.plugin.email.entity.EmailMessage;
import io.github.shineum.hermes_msg.plugin.email.EmailMessageClient;
import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class EmailMessageClient_SMTP extends EmailMessageClient {
    static Logger logger = LoggerFactory.getLogger(EmailMessageClient_SMTP.class);

    private String from = null;
    private String displayName = null;
    private Session session = null;

    @Override
    public IMessageClient initClient(Properties props) {
        final String authUser = props.getProperty("mail.smtp.user");
        final String authPassword = props.getProperty("mail.extra.secret");
        from = props.getProperty("mail.smtp.from", authUser);
        displayName = props.getProperty("mail.extra.displayname");

        Properties mailProps = new Properties();
        props.keySet().stream().map(Object::toString).filter(key -> key.startsWith("mail.smtp")).forEach(key -> {
            mailProps.put(key, props.getProperty(key));
        });

        Authenticator auth = null;
        if ("true".equals(mailProps.getProperty("mail.smtp.auth"))) {
            auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(authUser, authPassword);
                }
            };
        }
        this.session = Session.getInstance(mailProps, auth);
        return this;
    }

    private MimeBodyPart parseAttachment(ByteArrayAttachment byteArrayAttachment) {
        try {
            MimeBodyPart mbp = new MimeBodyPart();
            mbp.setFileName(byteArrayAttachment.getFilename());
            ByteArrayDataSource bads = new ByteArrayDataSource(
                    byteArrayAttachment.getByteArrayData(),
                    byteArrayAttachment.getContentType()
            );
            mbp.setDataHandler(new DataHandler(bads));
            return mbp;
        } catch (Exception e) {
            logger.error("[PARSE][ATTACHMENT]", e);
        }
        return null;
    }

    @Override
    public MessageResult send(EmailMessage emailMessage) {
        try {
            Message simpleMessage = new MimeMessage(session);
            // from
            {
                String fromEmail = Optional.ofNullable(emailMessage.getFrom()).orElse(from);
                String fromDisplayName = Optional.ofNullable(displayName).orElse(from);
                simpleMessage.setFrom(new InternetAddress(fromEmail, fromDisplayName));
            }
            // subject
            {
                simpleMessage.setSubject(emailMessage.getSubject());
            }
            // body (content)
            {
                String msgContentType = emailMessage.isHtml() ? "text/html" : "text/plain";
                List<ByteArrayAttachment> attachments = emailMessage.getAttachments();
                if (attachments != null && attachments.size() > 0) {
                    Multipart mp = new MimeMultipart();
                    {
                        MimeBodyPart mbp = new MimeBodyPart();
                        mbp.setContent(emailMessage.getBody(), msgContentType);
                        mp.addBodyPart(mbp);
                    }
                    attachments.forEach(attachment -> {
                        try {
                            mp.addBodyPart(parseAttachment(attachment));
                        } catch (Exception e) {
                            logger.error("[SEND][ADD_BODYPART]", e);
                        }
                    });
                    simpleMessage.setContent(mp);
                } else {
                    simpleMessage.setHeader("Content-type", msgContentType + "; charset=utf-8");
                    simpleMessage.setContent(emailMessage.getBody(), msgContentType);
                }
            }
            // recipient to
            Optional.ofNullable(emailMessage.getTo()).map(to -> {
                try {
                    simpleMessage.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
                } catch (Exception e) {
                    logger.error("[SEND][ADD_TO]", e);
                }
                return null;
            });
            // recipient cc
            Optional.ofNullable(emailMessage.getCc()).map(cc -> {
                try {
                    simpleMessage.addRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
                } catch (Exception e) {
                    logger.error("[SEND][ADD_CC]", e);
                }
                return null;
            });
            // recipient bcc
            Optional.ofNullable(emailMessage.getBcc()).map(bcc -> {
                try {
                    simpleMessage.addRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc));
                } catch (Exception e) {
                    logger.error("[SEND][ADD_BCC]", e);
                }
                return null;
            });
            // send
            Transport.send(simpleMessage);
        } catch (Exception e) {
            logger.error("[SEND][TRANSPORT]", e);
        }
        return null;
    }
}
