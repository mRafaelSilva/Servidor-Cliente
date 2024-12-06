import java.awt.SystemTray;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.net.*;

// 1 É TIPO REGISTO
// 2 É TIPO PEDIDO
// 3 É TIPO ACK
// 4 É TIPO ENVIO DE TAREFA
// 5 É TIPO RESULTADO


public class Server {

    private static final int TCP_PORT = 54321; // Porta para conexões TCP
    private static final int PORT = 12345;
    private DatagramSocket socket;
    private ServerSocket tcpSocket;
    private List<Integer> clients = new ArrayList<>();
    private AtomicInteger clientIdCounter = new AtomicInteger(1);
    private DatagramUtils utils;
    private AckHandle ackHandle;
    private InetAddress ipServidorIperf = null;

    public Server() throws IOException {
        socket = new DatagramSocket(PORT);
        tcpSocket = new ServerSocket(TCP_PORT);
        this.utils = new DatagramUtils();
        ackHandle = new AckHandle(socket,utils); // esta parte de iniciar vai ser assim visto que quero ter um socket a responder para cada cliente e depois fechá-lo?
        System.out.println("Servidor iniciado na porta UDP: " + PORT + " e na porta TCP " + TCP_PORT);
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
                System.out.println("O cliente com o id " + cliente + " está a pedir tarefas");
                    
                // envia um pacote com a tarefa
                List<String> taskCommands = readTasksFromJSON(cliente, clientAddress);
                if (taskCommands == null || taskCommands.isEmpty()) {
                    System.out.println("Nenhuma tarefa disponível para o cliente " + cliente);
                    return;
                }
                int tamanho = taskCommands.size();
                String str = String.valueOf(tamanho);

                String mensagemNTarefas = utils.criaDatagramaTarefaResultado(4, sequenceNumber, cliente, str);

                DatagramPacket nTarefasPacket = new DatagramPacket(
                    mensagemNTarefas.getBytes(), mensagemNTarefas.length(), clientAddress, clientPort
                );
                socket.send(nTarefasPacket);
                ackHandle.criaAckPendente(sequenceNumber, cliente, clientAddress, PORT, nTarefasPacket);
                
                // Iterar pela lista de comandos das tarefas
                for (int i = 0; i < taskCommands.size(); i++) {
                    String comandoTarefa = taskCommands.get(i); // Acessa o comando no índice atual
                    int taskSequenceNumber = sequenceNumber + i + 1; // Número de sequência único para cada tarefa

                    // Cria a mensagem de tarefa específica
                    String mensagemTarefa = utils.criaDatagramaTarefaResultado(4, taskSequenceNumber, cliente, comandoTarefa);

                    // Cria e envia o pacote
                    DatagramPacket taskPacket = new DatagramPacket(
                        mensagemTarefa.getBytes(), mensagemTarefa.length(), clientAddress, clientPort
                    );
                    socket.send(taskPacket);
                    System.out.println("Pacote enviado para a tarefa: " + comandoTarefa);

                    // Criar um ACK pendente para a tarefa enviada
                    ackHandle.criaAckPendente(taskSequenceNumber, cliente, clientAddress, clientPort, taskPacket);
                }

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
    private List<String> readTasksFromJSON(int clientId, InetAddress clientAddress) {
        List<String> taskList = new ArrayList<>();
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
    
                    String formattedTask = "task_id=" + taskId +",client_id=" + taskClientId + ",tipo_tarefa=" + tipo_tarefa +
                    ",command=" + command + ",frequency=" + frequency + ",limit=" + limit;
                    taskList.add(formattedTask);
                }   
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Tarefas para o cliente " + clientId + ": " + taskList);
        return taskList;
    }
    

    public void listenForAlerts() {
    new Thread(() -> {
        while (true) {
            try (Socket clientSocket = tcpSocket.accept()) {
                InputStream inputStream = clientSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String alertMessage = reader.readLine();

                System.out.println("Alerta recebido: " + alertMessage);

                // Tratamento do alerta
                processAlert(alertMessage);

                // Enviar ACK de volta ao cliente
                OutputStream outputStream = clientSocket.getOutputStream();
                PrintWriter writer = new PrintWriter(outputStream, true);
                writer.println("ACK");
            } catch (IOException e) {
                System.err.println("Erro ao lidar com conexão TCP: " + e.getMessage());
            }
        }
    }).start();
}

private void processAlert(String alertMessage) {
    // Exemplo de parsing e log do alerta
    String[] parts = alertMessage.split("\\|");
    if (parts.length < 6) {
        System.err.println("Alerta inválido recebido: " + alertMessage);
        return;
    }
    try {   
        double value = Double.parseDouble(parts[3]);
        String parameter = parts[2];              
        int clientId = Integer.parseInt(parts[1]); 
        double limit = Double.parseDouble(parts[5]);

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = now.format(formatter);

        // Construir mensagem formatada
        String logEntry = String.format("[%s] Alerta no cliente %d: o parâmetro %s tem o valor %.1f e ultrapassou o limite de %.1f.%n",
        timestamp, clientId, parameter, value, limit);

        // Construir o nome do ficheiro com base no ID do cliente
        String fileName = "Resultados/Cliente" + clientId + ".txt";
        // Registrar mensagem formatada no arquivo do cliente
        Files.write(Paths.get(fileName), logEntry.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);

        System.out.println("Alerta registrado no ficheiro: " + fileName);
        } catch (IOException e) {
            System.err.println("Erro ao processar alerta: " + e.getMessage());
        }
    }
    


    public static void main(String[] args) {
        Server server = null;
        try {
            server = new Server();
            Server finalServer = server; // Referência efetivamente final
            new Thread(() -> finalServer.listenForAlerts()).start();
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