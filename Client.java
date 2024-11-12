import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
//TO DO

// Fazer a confirmação pelo Ack no registo...
// Colocar as criações dos datagramas noutro ficheiro  


// Lógica de haver um nº que vá incrementando aqui (nº de sequencia), então o servidor não precisa de guardar nada né?
// mudar o id para tipo e acrescentar no datagrama o id do cliente;

public class Client {
    private static final int SERVER_PORT = 12345;
    private Integer clientId = null;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private AtomicInteger sequenceNumber = new AtomicInteger(1);


    public Client() throws IOException {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName("localhost");
    }

    public void register() {
        try {          
            int newSequenceNumber = sequenceNumber.getAndIncrement();
            String registrationMessage = createDatagramReg(1, newSequenceNumber); 
            DatagramPacket registrationPacket = new DatagramPacket(
                    registrationMessage.getBytes(), registrationMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(registrationPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);

            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            String[] parts = responseMessage.split("\\|");

            int type = Integer.parseInt(parts[0]);
            int sequenceNumber = Integer.parseInt(parts[1]);
            int receivedClientId = Integer.parseInt(parts[2]); // ID recebido do servidor

            if (3==(type)) {
                clientId = receivedClientId;
                System.out.println("Registo confirmado. Foi-me atribuído o id: "+ clientId);
            } else {
                System.out.println("Falha ao registar no servidor.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    public void requestTask() {
        try {
            int newSequenceNumber = sequenceNumber.getAndIncrement();
            String requestMessage = createDatagramReq(2, newSequenceNumber, clientId);
            DatagramPacket taskPacket = new DatagramPacket(
                    requestMessage.getBytes(), requestMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(taskPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);

            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("Resposta: " + responseMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void receiveTask() {
        try {
            byte[] buffer = new byte[1024];
            DatagramPacket taskPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(taskPacket);
    
            String taskMessage = new String(taskPacket.getData(), 0, taskPacket.getLength());
            String[] parts = taskMessage.split("\\|");
    
            int type = Integer.parseInt(parts[0]);
            String command = parts[4];
    
            if (4 == type) {
                System.out.println("Recebi a tarefa: " + command);
    
                int newSequenceNumber = sequenceNumber.getAndIncrement();
                // Envia o ACK ao servidor
                String ackMessage = createAckDatagram(3, newSequenceNumber, clientId);
                DatagramPacket ackPacket = new DatagramPacket(
                        ackMessage.getBytes(), ackMessage.length(), serverAddress, SERVER_PORT
                );
                socket.send(ackPacket);
                System.out.println("ACK enviado ao servidor.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
  

    private String createAckDatagram(int type, int seqNum, int clientId) {
        return type + "|" + seqNum + "|" + clientId;
    }
    


    private String createDatagramReg(int type, int sequenceNumber) {
        return type + "|"+ sequenceNumber ;
    }


    private String createDatagramReq(int type, int sequenceNumber, Integer id) {
    //    int size = info.length();
        return type + "|" + clientId + "|" + sequenceNumber;
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();
            client.register();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

