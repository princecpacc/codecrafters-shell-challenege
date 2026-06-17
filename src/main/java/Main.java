import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Split the input line by spaces to separate the command from arguments
            String[] commands = input.split(" ");
            String baseCommand = commands[0];

            if (baseCommand.equals("exit")) {
                break;
            } else if (baseCommand.equals("echo")) {
                // Slices off "echo " from the raw input line to print arguments properly
                System.out.println(input.substring(5));
            } else if (baseCommand.equals("type")) {
                String commandToCheck = commands[1];
                
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || commandToCheck.equals("type")) {
                    System.out.println(commandToCheck + " is a shell builtin");
                } else {
                    String path = getPath(commandToCheck);
                    if (path != null) {
                        System.out.println(commandToCheck + " is " + path);
                    } else {
                        System.out.println(commandToCheck + ": not found");
                    }
                }
            } else {
                // If it's not a builtin, check if it exists in the PATH environment variable
                String executablePath = getPath(baseCommand);
                
                if (executablePath != null) {
                    // Collect the base command and arguments to pass to ProcessBuilder
                    List<String> commandList = new ArrayList<>();
                    for (String arg : commands) {
                        commandList.add(arg);
                    }

                    // Create and start the external OS process
                    ProcessBuilder pb = new ProcessBuilder(commandList);
                    pb.inheritIO(); // Connects the process streams to the current terminal
                    Process process = pb.start();
                    process.waitFor(); // Block until execution finishes
                } else {
                    System.out.println(input + ": not found");
                }
            }
        }
    }

    // Helper method to scan the PATH environment variable for an executable
    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] directories = pathEnv.split(File.pathSeparator);
            for (String dir : directories) {
                File file = new File(dir, command);
                if (file.exists() && file.canExecute()) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }
}