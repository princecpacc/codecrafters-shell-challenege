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
                System.out.println(System.getProperty("user.dir"));
            } else if (baseCommand.equals("cd")) {
                String targetPath = input.length() > 3 ? input.substring(3).trim() : "~";
                
                // Handle home directory shortcut '~'
                if (targetPath.equals("~")) {
                    targetPath = System.getenv("HOME");
                }

                File targetDir = new File(targetPath);
                
                // Resolve relative paths (like '..' or '.') against the current working directory
                if (!targetDir.isAbsolute()) {
                    targetDir = new File(System.getProperty("user.dir"), targetPath);
                }

                // Verify the directory exists before switching
                if (targetDir.exists() && targetDir.isDirectory()) {
                    // Update Java's user.dir property to successfully change location
                    System.setProperty("user.dir", targetDir.getCanonicalPath());
                } else {
                    System.out.println("cd: " + targetPath + ": No such file or directory");
                }
            } else if (baseCommand.equals("type")) {
                String commandToCheck = commands[1];
                
                // Add "cd" to the list of known shell builtins
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || 
                    commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                    commandToCheck.equals("cd")) {
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
                    // CRITICAL: Ensure external programs run inside the updated directory location
                    pb.directory(new File(System.getProperty("user.dir")));
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