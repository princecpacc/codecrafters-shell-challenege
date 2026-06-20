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

            // --- TOKENIZER (QUOTES + BACKSLASH ESCAPES) ---
            List<String> tokens = new ArrayList<>();
            StringBuilder currentToken = new StringBuilder();
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inToken = false;

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (c == '\\') {
                    if (inSingleQuote) {
                        currentToken.append(c);
                        inToken = true;
                    } else if (inDoubleQuote) {
                        if (i + 1 < input.length()) {
                            char next = input.charAt(i + 1);
                            if (next == '"' || next == '\\' || next == '$') {
                                currentToken.append(next);
                                i++; 
                            } else {
                                currentToken.append(c); 
                            }
                        } else {
                            currentToken.append(c);
                        }
                        inToken = true;
                    } else {
                        if (i + 1 < input.length()) {
                            currentToken.append(input.charAt(i + 1));
                            i++; 
                        } else {
                            currentToken.append(c);
                        }
                        inToken = true;
                    }
                } else if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                    inToken = true; 
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                    inToken = true;
                } else if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                    if (inToken) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0); 
                        inToken = false;
                    }
                } else {
                    currentToken.append(c);
                    inToken = true;
                }
            }
            if (inToken) {
                tokens.add(currentToken.toString());
            }

            if (tokens.isEmpty()) continue;

            // --- ADVANCED REDIRECTION PARSING LAYER ---
            String stdoutFile = null;
            boolean stdoutAppend = false;
            String stderrFile = null;
            boolean stderrAppend = false;
            
            List<String> commandArgs = new ArrayList<>();

            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                
                // STDOUT Overwrite
                if ((token.equals(">") || token.equals("1>")) && i + 1 < tokens.size()) {
                    stdoutFile = tokens.get(i + 1);
                    stdoutAppend = false;
                    i++; 
                } 
                // STDOUT Append
                else if ((token.equals(">>") || token.equals("1>>")) && i + 1 < tokens.size()) {
                    stdoutFile = tokens.get(i + 1);
                    stdoutAppend = true;
                    i++;
                } 
                // STDERR Overwrite
                else if (token.equals("2>") && i + 1 < tokens.size()) {
                    stderrFile = tokens.get(i + 1);
                    stderrAppend = false;
                    i++;
                } 
                // STDERR Append
                else if (token.equals("2>>") && i + 1 < tokens.size()) {
                    stderrFile = tokens.get(i + 1);
                    stderrAppend = true;
                    i++;
                } 
                else {
                    commandArgs.add(token);
                }
            }

            if (commandArgs.isEmpty()) continue;
            String baseCommand = commandArgs.get(0);
            // ---------------------------------------------------------------

            if (baseCommand.equals("exit")) {
                break;
            } else if (baseCommand.equals("echo")) {
                StringBuilder echoOutput = new StringBuilder();
                for (int i = 1; i < commandArgs.size(); i++) {
                    echoOutput.append(commandArgs.get(i));
                    if (i < commandArgs.size() - 1) {
                        echoOutput.append(" ");
                    }
                }
                
                if (stdoutFile != null) {
                    File outFile = java.nio.file.Path.of(System.getProperty("user.dir")).resolve(stdoutFile).toFile();
                    if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                    
                    if (stdoutAppend) {
                        java.nio.file.Files.writeString(outFile.toPath(), echoOutput.toString() + "\n", 
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    } else {
                        java.nio.file.Files.writeString(outFile.toPath(), echoOutput.toString() + "\n", 
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } else {
                    System.out.println(echoOutput.toString());
                }
            } else if (baseCommand.equals("pwd")) {
                String pwdPath = System.getProperty("user.dir");
                if (stdoutFile != null) {
                    File outFile = java.nio.file.Path.of(pwdPath).resolve(stdoutFile).toFile();
                    if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

                    if (stdoutAppend) {
                        java.nio.file.Files.writeString(outFile.toPath(), pwdPath + "\n", 
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    } else {
                        java.nio.file.Files.writeString(outFile.toPath(), pwdPath + "\n", 
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } else {
                    System.out.println(pwdPath);
                }
            } else if (baseCommand.equals("cd")) {
                String targetPath = commandArgs.size() > 1 ? commandArgs.get(1) : "~";
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
                    if (commandArgs.size() > 1 && commandArgs.get(1).equals("-")) {
                        System.out.println(canonicalPath);
                    }
                } else {
                    System.out.println("cd: " + targetPath + ": No such file or directory");
                }
            } else if (baseCommand.equals("type")) {
                if (commandArgs.size() < 2) {
                    System.out.println("type: missing operand");
                    continue;
                }
                String commandToCheck = commandArgs.get(1);
                String result;
                
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || 
                    commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                    commandToCheck.equals("cd")) {
                    result = commandToCheck + " is a shell builtin";
                } else {
                    String path = getPath(commandToCheck);
                    if (path != null) {
                        result = commandToCheck + " is " + path;
                    } else {
                        result = commandToCheck + ": not found";
                    }
                }

                if (stdoutFile != null) {
                    File outFile = java.nio.file.Path.of(System.getProperty("user.dir")).resolve(stdoutFile).toFile();
                    if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

                    if (stdoutAppend) {
                        java.nio.file.Files.writeString(outFile.toPath(), result + "\n", 
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    } else {
                        java.nio.file.Files.writeString(outFile.toPath(), result + "\n", 
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } else {
                    System.out.println(result);
                }
            } else {
                String executablePath = getPath(baseCommand);
                
                if (executablePath != null) {
                    ProcessBuilder pb = new ProcessBuilder(commandArgs);
                    pb.directory(new File(System.getProperty("user.dir")));
                    
                    // Handle Standard Output (stdout) Redirection
                    if (stdoutFile != null) {
                        File outFile = java.nio.file.Path.of(System.getProperty("user.dir")).resolve(stdoutFile).toFile();
                        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                        
                        if (stdoutAppend) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.to(outFile));
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    // Handle Standard Error (stderr) Redirection
                    if (stderrFile != null) {
                        File errFile = java.nio.file.Path.of(System.getProperty("user.dir")).resolve(stderrFile).toFile();
                        if (errFile.getParentFile() != null) errFile.getParentFile().mkdirs();
                        
                        if (stderrAppend) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.to(errFile));
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
                    
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                    
                    Process process = pb.start();
                    process.waitFor(); 
                } else {
                    System.out.println(baseCommand + ": not found");
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