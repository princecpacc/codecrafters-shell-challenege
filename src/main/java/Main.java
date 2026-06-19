import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static String oldWorkingDirectory = null;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) break;
            
            // Do NOT use .trim() here, let the tokenizer handle all spaces natively
            String input = scanner.nextLine();
            if (input.trim().isEmpty()) {
                continue;
            }

            // --- REAL SHELL TOKENIZER (CHARACTER-BY-CHARACTER) ---
            List<String> tokens = new ArrayList<>();
            StringBuilder currentToken = new StringBuilder();
            boolean inSingleQuote = false;
            boolean inToken = false;

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (c == '\'') {
                    // Toggle quote state. Quotes mean we are definitely inside a token.
                    inSingleQuote = !inSingleQuote;
                    inToken = true; 
                } else if (c == ' ' && !inSingleQuote) {
                    // A space OUTSIDE of quotes means the token is finished
                    if (inToken) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0); // Reset for the next token
                        inToken = false;
                    }
                } else {
                    // Normal characters (or spaces INSIDE quotes) get appended
                    currentToken.append(c);
                    inToken = true;
                }
            }
            // Catch the very last token if the string ended without a trailing space
            if (inToken) {
                tokens.add(currentToken.toString());
            }

            if (tokens.isEmpty()) continue;

            String baseCommand = tokens.get(0);
            // -----------------------------------------------------

            if (baseCommand.equals("exit")) {
                break;
            } else if (baseCommand.equals("echo")) {
                StringBuilder echoOutput = new StringBuilder();
                for (int i = 1; i < tokens.size(); i++) {
                    echoOutput.append(tokens.get(i));
                    if (i < tokens.size() - 1) {
                        echoOutput.append(" ");
                    }
                }
                System.out.println(echoOutput.toString());
            } else if (baseCommand.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (baseCommand.equals("cd")) {
                String targetPath = tokens.size() > 1 ? tokens.get(1) : "~";
                String currentDir = System.getProperty("user.dir");
                
                if (targetPath.equals("-")) {
                    if (oldWorkingDirectory == null) {
                        System.out.println("cd: OLDPWD not set");
                        continue;
                    }
                    targetPath = oldWorkingDirectory;
                } else if (targetPath.equals("~")) {
                    targetPath = System.getenv("HOME");
                }

                File targetDir = new File(targetPath);
                if (!targetDir.isAbsolute()) {
                    targetDir = new File(currentDir, targetPath);
                }

                if (targetDir.exists() && targetDir.isDirectory()) {
                    String canonicalPath = targetDir.getCanonicalPath();
                    oldWorkingDirectory = currentDir;
                    System.setProperty("user.dir", canonicalPath);
                    if (tokens.size() > 1 && tokens.get(1).equals("-")) {
                        System.out.println(canonicalPath);
                    }
                } else {
                    System.out.println("cd: " + targetPath + ": No such file or directory");
                }
            } else if (baseCommand.equals("type")) {
                if (tokens.size() < 2) {
                    System.out.println("type: missing operand");
                    continue;
                }
                String commandToCheck = tokens.get(1);
                
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
                    ProcessBuilder pb = new ProcessBuilder(tokens);
                    pb.directory(new File(System.getProperty("user.dir")));
                    pb.inheritIO(); 
                    Process process = pb.start();
                    process.waitFor(); 
                } else {
                    System.out.println(baseCommand + ": command not found");
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