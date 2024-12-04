import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 1 É TIPO REGISTO
// 2 É TIPO PEDIDO
// 3 É TIPO ACK
// 4 É TIPO ENVIO DE TAREFA
// 5 É TIPO RESULTADO


public class Server {

    private static final int PORT = 12345;
    private DatagramSocket socket;
    private List<Integer> clients = new ArrayList<>();
    private AtomicInteger clientIdCounter = new AtomicInteger(1);
    private DatagramUtils utils;
    private AckHandle ackHandle;
    private InetAddress ipServidorIperf = null;

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

                String fileName = "Resultados/Cliente" + newClientId + ".txt";
                Files.write(Paths.get(fileName), ("Cliente " + newClientId + " registrado.\n").getBytes());

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
                String taskCommand = readTaskFromJSON(cliente, clientAddress);
                if (taskCommand == null) {
                    return;
                }
                String taskMessage = utils.criaDatagramaTarefa(4, sequenceNumber, cliente, taskCommand);

                DatagramPacket taskPacket = new DatagramPacket(
                        taskMessage.getBytes(), taskMessage.length(), clientAddress, clientPort
                );

                socket.send(taskPacket);
                System.out.println("Pacote de tarefa enviado.");

                    
                // CRIO UM ACK
                System.out.println("Chamando criaAckPendente para seq=" + sequenceNumber + " e id=" + cliente);

                ackHandle.criaAckPendente(sequenceNumber, cliente, clientAddress, clientPort, taskPacket);
                return;     
            } 

            if (type == 3) {
                System.out.println("Recebi um ack");
                ackHandle.processAck(sequenceNumber, cliente);
                return;
            }
        
            if (type == 5) {
                String result = parts[3];

                System.out.println("Resultado recebido do cliente " + cliente + ":" + result);
                ackHandle.sendAck(sequenceNumber, cliente, clientAddress, clientPort, socket);

                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String timestamp = now.format(formatter);

                String fileName = "Resultados/Cliente" + cliente + ".txt";
                String resultEntry = "[" + timestamp + "] " + result + "\n";
                Files.write(Paths.get(fileName), resultEntry.getBytes(), StandardOpenOption.APPEND);

                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Tirar esta função daqui
    private String readTaskFromJSON(int clientId, InetAddress clientAddress) {
        try {
            String conteudo = new String(Files.readAllBytes(Paths.get("tasks.json")));
            JSONObject jsonObject = new JSONObject(conteudo);
            JSONArray tasks = jsonObject.getJSONArray("tasks");
    
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                int taskClientId = task.getInt("client_id");
                if (taskClientId == clientId) {
                    String taskId = task.getString("task_id");
                    int tipo_tarefa = task.getInt("tipo_tarefa");

                    if (tipo_tarefa == 2) {
                        ipServidorIperf = clientAddress;
                    }

                    String command = task.getString("command");

                    if (tipo_tarefa == 3) {
                        command = command.replace("<server_ip>", ipServidorIperf.getHostAddress());
                        if (ipServidorIperf == null) {
                            System.out.println("Erro: Nenhum servidor iperf configurado para o cliente " + clientId);
                            return null;
                        }
                    }


                    int frequency = task.getInt("frequency");
                    long limit = task.getLong("limit");
    
                    return "task_id=" + taskId +  ",tipo_tarefa=" + tipo_tarefa + ",command=" + command + ",frequency=" + frequency + ",limit=" + limit;
                }   
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    

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