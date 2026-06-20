import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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

            // --- TOKENIZER ---
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

            // --- PIPELINE SPLITTING ---
            List<List<String>> pipelines = new ArrayList<>();
            List<String> currentPipeline = new ArrayList<>();
            for (String t : tokens) {
                if (t.equals("|")) {
                    pipelines.add(currentPipeline);
                    currentPipeline = new ArrayList<>();
                } else {
                    currentPipeline.add(t);
                }
            }
            pipelines.add(currentPipeline);

            executePipelines(pipelines);
        }
    }

    private static void executePipelines(List<List<String>> pipelines) throws Exception {
        List<ProcessBuilder> pbs = new ArrayList<>();
        File lastOutput = null;
        List<File> tempFiles = new ArrayList<>();

        for (int i = 0; i < pipelines.size(); i++) {
            List<String> cmd = pipelines.get(i);
            if (cmd.isEmpty()) continue;

            String baseCommand = cmd.get(0);
            boolean isBuiltin = baseCommand.equals("echo") || baseCommand.equals("pwd") || 
                                baseCommand.equals("cd") || baseCommand.equals("type") || 
                                baseCommand.equals("exit");

            if (isBuiltin) {
                // Flush accumulated external commands
                if (!pbs.isEmpty()) {
                    File currentOutput = null;
                    if (i < pipelines.size()) {
                        currentOutput = File.createTempFile("pipe", ".tmp");
                        tempFiles.add(currentOutput);
                    }
                    flushPbs(pbs, lastOutput, currentOutput);
                    lastOutput = currentOutput;
                }

                File currentOutput = null;
                if (i < pipelines.size() - 1) {
                    currentOutput = File.createTempFile("pipe", ".tmp");
                    tempFiles.add(currentOutput);
                }
                
                executeBuiltin(cmd, currentOutput);
                lastOutput = currentOutput;

            } else {
                ProcessBuilder pb = createProcessBuilder(cmd);
                if (pb == null) {
                    // Command not found
                    if (!pbs.isEmpty()) {
                        flushPbs(pbs, lastOutput, null);
                    }
                    System.out.println(baseCommand + ": not found");
                    lastOutput = null;
                    continue;
                }

                pbs.add(pb);
            }
        }

        if (!pbs.isEmpty()) {
            flushPbs(pbs, lastOutput, null);
        }

        for (File f : tempFiles) {
            f.delete();
        }
    }

    private static void flushPbs(List<ProcessBuilder> pbs, File lastOutput, File currentOutput) throws Exception {
        if (pbs.isEmpty()) return;
        ProcessBuilder first = pbs.get(0);
        if (first.redirectInput() == ProcessBuilder.Redirect.PIPE) {
            if (lastOutput != null) {
                first.redirectInput(lastOutput);
            } else {
                first.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }
        }
        
        ProcessBuilder last = pbs.get(pbs.size() - 1);
        if (last.redirectOutput() == ProcessBuilder.Redirect.PIPE) {
            if (currentOutput != null) {
                last.redirectOutput(currentOutput);
            } else {
                last.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
        }
        
        List<Process> processes = ProcessBuilder.startPipeline(pbs);
        processes.get(processes.size() - 1).waitFor();
        pbs.clear();
    }

    private static void executeBuiltin(List<String> tokens, File pipeOut) throws Exception {
        String stdoutFile = null;
        boolean stdoutAppend = false;
        String stderrFile = null;
        boolean stderrAppend = false;
        
        List<String> commandArgs = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            
            if ((token.equals(">") || token.equals("1>")) && i + 1 < tokens.size()) {
                stdoutFile = tokens.get(i + 1);
                stdoutAppend = false;
                i++; 
            } else if ((token.equals(">>") || token.equals("1>>")) && i + 1 < tokens.size()) {
                stdoutFile = tokens.get(i + 1);
                stdoutAppend = true;
                i++;
            } else if (token.equals("2>") && i + 1 < tokens.size()) {
                stderrFile = tokens.get(i + 1);
                stderrAppend = false;
                i++;
            } else if (token.equals("2>>") && i + 1 < tokens.size()) {
                stderrFile = tokens.get(i + 1);
                stderrAppend = true;
                i++;
            } else {
                commandArgs.add(token);
            }
        }

        if (commandArgs.isEmpty()) return;
        String baseCommand = commandArgs.get(0);

        try {
            if (stdoutFile != null) {
                File outFile = Path.of(System.getProperty("user.dir")).resolve(stdoutFile).toFile();
                if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                if (!stdoutAppend) outFile.delete();
                if (!outFile.exists()) outFile.createNewFile();
            }
            if (stderrFile != null) {
                File errFile = Path.of(System.getProperty("user.dir")).resolve(stderrFile).toFile();
                if (errFile.getParentFile() != null) errFile.getParentFile().mkdirs();
                if (!stderrAppend) errFile.delete();
                if (!errFile.exists()) errFile.createNewFile();
            }
        } catch (Exception e) {}

        File finalStdoutFile = null;
        boolean finalStdoutAppend = false;
        if (stdoutFile != null) {
            finalStdoutFile = Path.of(System.getProperty("user.dir")).resolve(stdoutFile).toFile();
            finalStdoutAppend = stdoutAppend;
        } else if (pipeOut != null) {
            finalStdoutFile = pipeOut;
            finalStdoutAppend = false;
        }

        if (baseCommand.equals("exit")) {
            System.exit(0);
        } else if (baseCommand.equals("echo")) {
            StringBuilder echoOutput = new StringBuilder();
            for (int i = 1; i < commandArgs.size(); i++) {
                echoOutput.append(commandArgs.get(i));
                if (i < commandArgs.size() - 1) {
                    echoOutput.append(" ");
                }
            }
            writeOutput(finalStdoutFile, finalStdoutAppend, echoOutput.toString());
        } else if (baseCommand.equals("pwd")) {
            String pwdPath = System.getProperty("user.dir");
            writeOutput(finalStdoutFile, finalStdoutAppend, pwdPath);
        } else if (baseCommand.equals("cd")) {
            String targetPath = commandArgs.size() > 1 ? commandArgs.get(1) : "~";
            String currentDir = System.getProperty("user.dir");
            
            if (targetPath.equals("-")) {
                if (oldWorkingDirectory == null) {
                    System.out.println("cd: OLDPWD not set");
                    return;
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
                    writeOutput(finalStdoutFile, finalStdoutAppend, canonicalPath);
                }
            } else {
                System.out.println("cd: " + targetPath + ": No such file or directory");
            }
        } else if (baseCommand.equals("type")) {
            if (commandArgs.size() < 2) {
                System.out.println("type: missing operand");
                return;
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
            writeOutput(finalStdoutFile, finalStdoutAppend, result);
        }
    }

    private static ProcessBuilder createProcessBuilder(List<String> tokens) {
        String stdoutFile = null;
        boolean stdoutAppend = false;
        String stderrFile = null;
        boolean stderrAppend = false;
        
        List<String> commandArgs = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            
            if ((token.equals(">") || token.equals("1>")) && i + 1 < tokens.size()) {
                stdoutFile = tokens.get(i + 1);
                stdoutAppend = false;
                i++; 
            } else if ((token.equals(">>") || token.equals("1>>")) && i + 1 < tokens.size()) {
                stdoutFile = tokens.get(i + 1);
                stdoutAppend = true;
                i++;
            } else if (token.equals("2>") && i + 1 < tokens.size()) {
                stderrFile = tokens.get(i + 1);
                stderrAppend = false;
                i++;
            } else if (token.equals("2>>") && i + 1 < tokens.size()) {
                stderrFile = tokens.get(i + 1);
                stderrAppend = true;
                i++;
            } else {
                commandArgs.add(token);
            }
        }

        if (commandArgs.isEmpty()) return null;
        String baseCommand = commandArgs.get(0);
        String executablePath = getPath(baseCommand);
        
        if (executablePath == null) return null;

        ProcessBuilder pb = new ProcessBuilder(commandArgs);
        pb.directory(new File(System.getProperty("user.dir")));

        try {
            if (stdoutFile != null) {
                File outFile = Path.of(System.getProperty("user.dir")).resolve(stdoutFile).toFile();
                if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                if (!stdoutAppend) outFile.delete();
                if (!outFile.exists()) outFile.createNewFile();
                
                if (stdoutAppend) {
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.to(outFile));
                }
            }

            if (stderrFile != null) {
                File errFile = Path.of(System.getProperty("user.dir")).resolve(stderrFile).toFile();
                if (errFile.getParentFile() != null) errFile.getParentFile().mkdirs();
                if (!stderrAppend) errFile.delete();
                if (!errFile.exists()) errFile.createNewFile();
                
                if (stderrAppend) {
                    pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.to(errFile));
                }
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }
        } catch (Exception e) {}
        
        return pb;
    }

    private static void writeOutput(File file, boolean append, String content) throws Exception {
        if (file != null) {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), content + "\n", 
                StandardOpenOption.CREATE, 
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            System.out.print(content + "\n");
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