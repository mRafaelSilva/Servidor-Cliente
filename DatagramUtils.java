public class DatagramUtils {
    
    //Cria datagrama que envia a tarefa e que envia resultados
    public String criaDatagramaTarefaResultado(int type, int seqNum, int clientId, String command) {
        return type + "|" + seqNum + "|" + clientId + "|" + command;
    }

    //Usado em Acks ou para o cliente pedir Tarefas
    public String criaDatagramaNormal(int type, int seqNum, int clientId) {
        return type + "|" + seqNum + "|" + clientId;
    }

    public String criaDatagramaRegisto(int type, int sequenceNumber) {
        return type + "|" + sequenceNumber;
    }

}


