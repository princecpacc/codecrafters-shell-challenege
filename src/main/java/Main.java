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

            String[] commands = input.split(" ");
            String baseCommand = commands[0];

            if (baseCommand.equals("exit")) {
                break;
            } else if (baseCommand.equals("echo")) {
                System.out.println(input.substring(5));
            } else if (baseCommand.equals("pwd")) {
                // Fetch and print the current working directory absolute path
                System.out.println(System.getProperty("user.dir"));
            } else if (baseCommand.equals("type")) {
                String commandToCheck = commands[1];
                
                // Add "pwd" to the list of known shell builtins
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || commandToCheck.equals("type") || commandToCheck.equals("pwd")) {
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
                String executablePath = getPath(baseCommand);
                
                if (executablePath != null) {
                    List<String> commandList = new ArrayList<>();
                    for (String arg : commands) {
                        commandList.add(arg);
                    }

                    ProcessBuilder pb = new ProcessBuilder(commandList);
                    pb.inheritIO(); 
                    Process process = pb.start();
                    process.waitFor(); 
                } else {
                    System.out.println(input + ": not found");
                }
            }
        }
    }

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