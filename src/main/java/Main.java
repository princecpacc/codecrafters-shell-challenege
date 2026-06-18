import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    // Keep track of the previous working directory globally
    private static String oldWorkingDirectory = null;

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
                String currentDir = System.getProperty("user.dir");
                
                // Handle "cd -" to toggle back to the previous directory
                if (targetPath.equals("-")) {
                    if (oldWorkingDirectory == null) {
                        // Standard shell behavior if OLDPWD isn't set yet
                        System.out.println("cd: OLDPWD not set");
                        continue;
                    }
                    targetPath = oldWorkingDirectory;
                }
                // Handle home directory shortcut '~'
                else if (targetPath.equals("~")) {
                    targetPath = System.getenv("HOME");
                }

                File targetDir = new File(targetPath);
                if (!targetDir.isAbsolute()) {
                    targetDir = new File(currentDir, targetPath);
                }

                if (targetDir.exists() && targetDir.isDirectory()) {
                    String canonicalPath = targetDir.getCanonicalPath();
                    
                    // Track the previous directory before updating the environment
                    oldWorkingDirectory = currentDir;
                    System.setProperty("user.dir", canonicalPath);
                    
                    // Standard shells print the destination path when navigating via '-'
                    if (input.substring(3).trim().equals("-")) {
                        System.out.println(canonicalPath);
                    }
                } else {
                    System.out.println("cd: " + targetPath + ": No such file or directory");
                }
            } else if (baseCommand.equals("type")) {
                String commandToCheck = commands[1];
                
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