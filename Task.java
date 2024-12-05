public class Task {
    private final String taskId;
    private final int clientId;
    private final int tipoTarefa;
    private final String command;
    private final int frequency;
    private final long limite;

    public Task(String taskId, int clientId, int tipoTarefa, String command, int frequency, long limite) {
        this.taskId = taskId;
        this.clientId = clientId;
        this.tipoTarefa = tipoTarefa;
        this.command = command;
        this.frequency = frequency;
        this.limite = limite;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getClientId() {
        return clientId;
    }

    public int getTipoTarefa() {
        return tipoTarefa;
    }

    public String getCommand() {
        return command;
    }

    public int getFrequency() {
        return frequency;
    }

    public long getLimite() {
        return limite;
    }
}

