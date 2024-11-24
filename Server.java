import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

public class Server {

    private static final int PORT = 12345;
    private DatagramSocket socket;
    private List<Integer> clients = new ArrayList<>();
    private AtomicInteger clientIdCounter = new AtomicInteger(1);
    private DatagramUtils utils;
    private AckHandle ackHandle;

    public Server() throws IOException {
        socket = new DatagramSocket(PORT);
        this.utils = new DatagramUtils();
        ackHandle = new AckHandle(socket,utils); // esta parte de iniciar vai ser assim visto que quero ter um socket a responder para cada cliente e depois fechá-lo?
        System.out.println("Servidor iniciado na porta " + PORT);
    }

    public void listen() {
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                new Thread(() -> handleClient(packet)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleClient(DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength());
            String[] parts = message.split("\\|");

            int type = Integer.parseInt(parts[0]);
            int sequenceNumber = Integer.parseInt(parts[1]);
            
            InetAddress clientAddress = packet.getAddress();
            int clientPort = packet.getPort();

            if (type == 1) {
                // gera um novo id para o cliente
                int newClientId = clientIdCounter.getAndIncrement();
                clients.add(newClientId);

                ackHandle.sendEGuardaAck(sequenceNumber, newClientId, clientAddress, clientPort, socket);

                return;            
            }

            if (parts.length < 3 || parts[2] == null || parts[2].isEmpty()) {
                System.out.println("Mensagem inválida recebida, ignorando.");
                return;
            }

            int cliente = Integer.parseInt(parts[2]);

            if (!clients.contains(cliente)) {
                System.out.println("Cliente desconhecido: " + cliente);
                return;
            }

            if (type == 2) {
                System.out.println("O cliente com o id " + cliente + " está a pedir uma tarefa");
                    
                // envia um pacote com a tarefa
                String taskCommand = readTaskFromJSON(cliente);
                String taskMessage = utils.criaDatagramaTarefa(4, sequenceNumber, cliente, taskCommand);
                
                DatagramPacket taskPacket = new DatagramPacket(
                        taskMessage.getBytes(), taskMessage.length(), clientAddress, clientPort
                );
                socket.send(taskPacket);
                    
                // CRIO UM ACK
                ackHandle.criaAckPendente(sequenceNumber, cliente, clientAddress, clientPort, taskPacket);
                return;     
            } 

            if (type == 3) {
                ackHandle.processAck(sequenceNumber, cliente);
                return;
            }
        
            if (type == 5) {
                String result = parts[3];

                System.out.println("Resultado recebido do cliente " + cliente + ":" + result);
                ackHandle.sendAck(sequenceNumber, cliente, clientAddress, clientPort, socket);

                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Tirar esta função daqui
    private String readTaskFromJSON(int clientId) {
        try {
            String conteudo = new String(Files.readAllBytes(Paths.get("tasks.json")));
            JSONObject jsonObject = new JSONObject(conteudo);
            JSONArray tasks = jsonObject.getJSONArray("tasks");
    
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                int taskClientId = task.getInt("client_id");
                if (taskClientId == clientId) {
                    String taskId = task.getString("task_id");
                    String command = task.getString("command");
                    int frequency = task.getInt("frequency");
    
                    return "task_id=" + taskId + ",command=" + command + ",frequency=" + frequency;
                }   
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    

    // FALTA DEIXAR SEMPRE A RODAR A VERIFICAÇÃO DOS ACKS E RETRANSMISSÃO
    // FALTAR VER SE CRIO A LISTA AQUI OU NÃO JÁ QUE O CLIENTE TAMBÉM VAI
    // TER UMA LISTA E PODE DAR CONFLITO COMO O ACKHANDLE SÓ TEM UMA
    public static void main(String[] args) {
        Server server = null;
        try {
            server = new Server();
            server.listen();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (server != null && server.ackHandle != null) {
                server.ackHandle.stopRetransmissionTask();
            }
        }
    }
}