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
            
            String input = scanner.nextLine();
            if (input.trim().isEmpty()) {
                continue;
            }

            // --- ULTIMATE TOKENIZER (QUOTES + BACKSLASH ESCAPES) ---
            List<String> tokens = new ArrayList<>();
            StringBuilder currentToken = new StringBuilder();
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inToken = false;

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                // 1. Handle Backslashes
                if (c == '\\') {
                    if (inSingleQuote) {
                        // Inside single quotes, backslash is just a literal character
                        currentToken.append(c);
                        inToken = true;
                    } else if (inDoubleQuote) {
                        // Inside double quotes, it ONLY escapes ", \, and $
                        if (i + 1 < input.length()) {
                            char next = input.charAt(i + 1);
                            if (next == '"' || next == '\\' || next == '$') {
                                currentToken.append(next);
                                i++; // Skip the next character since we just escaped it
                            } else {
                                currentToken.append(c); // Not a special char, keep the literal backslash
                            }
                        } else {
                            currentToken.append(c);
                        }
                        inToken = true;
                    } else {
                        // Outside quotes, it cleanly escapes whatever the very next character is (like a space)
                        if (i + 1 < input.length()) {
                            currentToken.append(input.charAt(i + 1));
                            i++; // Skip the escaped character
                        } else {
                            currentToken.append(c);
                        }
                        inToken = true;
                    }
                } 
                // 2. Handle Single Quotes
                else if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                    inToken = true; 
                } 
                // 3. Handle Double Quotes
                else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                    inToken = true;
                } 
                // 4. Handle Spaces (Token Separators)
                else if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                    if (inToken) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0); 
                        inToken = false;
                    }
                } 
                // 5. Normal Characters
                else {
                    currentToken.append(c);
                    inToken = true;
                }
            }
            
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