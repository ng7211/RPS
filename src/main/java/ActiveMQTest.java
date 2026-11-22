// AI-GENERATED-START: ActiveMQTest class - 22.11.2026
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

public class ActiveMQTest {

    public static void main(String[] args) {
        String brokerUrl = "tcp://localhost:61616";
        String queueName = "test.queue";

        // ConnectionFactory initialisieren
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);

        try {
            // 1. Verbindung herstellen und starten
            Connection connection = connectionFactory.createConnection();
            connection.start();

            // 2. Session erstellen (keine Transaktion, Auto-Acknowledge)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // 3. Ziel-Queue definieren
            Destination destination = session.createQueue(queueName);

            // --- SCHRITT 2: Nachricht in test.queue legen ---
            MessageProducer producer = session.createProducer(destination);
            TextMessage sendTextMsg = session.createTextMessage("Hallo ActiveMQ 6.3.3 - Testnachricht!");
            producer.send(sendTextMsg);
            System.out.println("-> Nachricht gesendet: " + sendTextMsg.getText());

            // Prüfe jetzt in der Web-Konsole: „Messages Enqueued“ sollte auf 1 stehen!

            producer.close();

            // --- SCHRITT 4: Nachricht wieder lesen ---
            MessageConsumer consumer = session.createConsumer(destination);

            // Wir warten kurz (max. 3 Sekunden) auf die Nachricht
            Message receivedMessage = consumer.receive(3000);

            if (receivedMessage instanceof TextMessage receiveTextMsg) {
                System.out.println("<- Nachricht empfangen: " + receiveTextMsg.getText());
            } else {
                System.out.println("Keine Nachricht empfangen.");
            }

            // Ressourcen aufräumen
            consumer.close();
            session.close();
            connection.close();

            System.out.println("Test erfolgreich abgeschlossen! „Dequeued“ sollte jetzt auf 1 stehen.");

        } catch (JMSException e) {
            System.err.println("Fehler bei der JMS-Verarbeitung:");
            e.printStackTrace();
        }
    }
}