import java.util.*;
import java.io.*;

public class Main {

    private static final Set<String> BUILTINS = Set.of("echo", "exit", "type", "pwd", "cd", "jobs");
    private static final List<Job> JOBS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            reapCompletedJobs(new PrintStream(System.out, true));
            System.out.print("$ ");
            String input = scanner.nextLine();
            if (input.isBlank()) continue;

            List<List<Token>> pipeline = parsePipeline(input);
            if (pipeline.isEmpty()) continue;
            executePipeline(pipeline);
        }
    }

    record Token(String value, boolean quoted) {}

    static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == ' ') { i++; continue; }
            if (c == '|') { tokens.add(new Token("|", false)); i++; continue; }
            if (c == '<') { tokens.add(new Token("<", false)); i++; continue; }

            if ((c == '1' || c == '2') && i + 1 < input.length() && input.charAt(i + 1) == '>') {
                if (i + 2 < input.length() && input.charAt(i + 2) == '>') {
                    tokens.add(new Token(c + ">>", false)); i += 3;
                } else {
                    tokens.add(new Token(c + ">", false)); i += 2;
                }
                continue;
            }

            if (c == '>') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                    tokens.add(new Token(">>", false)); i += 2;
                } else {
                    tokens.add(new Token(">", false)); i++;
                }
                continue;
            }

            StringBuilder sb = new StringBuilder();
            boolean quoted = false;
            while (i < input.length()) {
                c = input.charAt(i);
                if (c == ' ') break;
                if (c == '|' || c == '<') break;
                if (c == '>') break;
                if ((c == '1' || c == '2') && i + 1 < input.length() && input.charAt(i + 1) == '>') break;

                if (c == '\'') {
                    quoted = true;
                    i++;
                    while (i < input.length() && input.charAt(i) != '\'') {
                        sb.append(input.charAt(i++));
                    }
                    if (i < input.length()) i++;
                } else if (c == '"') {
                    quoted = true;
                    i++;
                    while (i < input.length() && input.charAt(i) != '"') {
                        if (input.charAt(i) == '\\') {
                            i++;
                            if (i < input.length()) {
                                char n = input.charAt(i);
                                if (n == '\\' || n == '"' || n == '$' || n == '`') {
                                    sb.append(n);
                                } else {
                                    sb.append('\\').append(n);
                                }
                                i++;
                            }
                        } else {
                            sb.append(input.charAt(i++));
                        }
                    }
                    if (i < input.length()) i++;
                } else if (c == '\\') {
                    i++;
                    if (i < input.length()) sb.append(input.charAt(i++));
                } else {
                    sb.append(c);
                    i++;
                }
            }
            if (!sb.isEmpty() || quoted) tokens.add(new Token(sb.toString(), quoted));
        }
        return tokens;
    }

    static List<List<Token>> parsePipeline(String input) {
        List<Token> tokens = tokenize(input);
        List<List<Token>> pipeline = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        for (Token t : tokens) {
            if (t.value().equals("|") && !t.quoted()) {
                if (!current.isEmpty()) pipeline.add(current);
                current = new ArrayList<>();
            } else {
                current.add(t);
            }
        }
        if (!current.isEmpty()) pipeline.add(current);
        return pipeline;
    }

    record Job(int number, Process process, String command) {}

    record ParsedCmd(String cmd, List<String> args,
                     String redirectIn, String redirectOut, boolean appendOut,
                     String redirectErr, boolean appendErr,
                     boolean background, String displayCommand) {}

    static String expandHome(Token t) {
        if (t.quoted()) return t.value();
        String val = t.value();
        if (val.equals("~")) {
            String home = System.getenv("HOME");
            return home != null ? home : System.getProperty("user.home");
        }
        if (val.startsWith("~/")) {
            String home = System.getenv("HOME");
            if (home == null) home = System.getProperty("user.home");
            return home + val.substring(1);
        }
        return val;
    }

    static ParsedCmd parseCmd(List<Token> tokens) {
        boolean background = false;
        if (!tokens.isEmpty()) {
            Token last = tokens.get(tokens.size() - 1);
            if (!last.quoted() && last.value().equals("&")) {
                background = true;
                tokens = new ArrayList<>(tokens.subList(0, tokens.size() - 1));
            }
        }

        StringBuilder display = new StringBuilder();
        for (Token token : tokens) {
            if (!display.isEmpty()) display.append(' ');
            display.append(token.value());
        }

        String cmd = null;
        List<String> args = new ArrayList<>();
        String redirectIn = null, redirectOut = null, redirectErr = null;
        boolean appendOut = false, appendErr = false;

        for (int i = 0; i < tokens.size();) {
            Token t = tokens.get(i);
            String val = t.value();
            boolean isOp = !t.quoted();

            if (isOp && val.equals("<") && i + 1 < tokens.size()) {
                redirectIn = expandHome(tokens.get(i + 1)); i += 2;
            } else if (isOp && (val.equals(">") || val.equals("1>")) && i + 1 < tokens.size()) {
                redirectOut = expandHome(tokens.get(i + 1)); appendOut = false; i += 2;
            } else if (isOp && (val.equals(">>") || val.equals("1>>")) && i + 1 < tokens.size()) {
                redirectOut = expandHome(tokens.get(i + 1)); appendOut = true; i += 2;
            } else if (isOp && val.equals("2>") && i + 1 < tokens.size()) {
                redirectErr = expandHome(tokens.get(i + 1)); appendErr = false; i += 2;
            } else if (isOp && val.equals("2>>") && i + 1 < tokens.size()) {
                redirectErr = expandHome(tokens.get(i + 1)); appendErr = true; i += 2;
            } else {
                if (cmd == null) {
                    cmd = expandHome(t);
                } else {
                    args.add(expandHome(t));
                }
                i++;
            }
        }
        return new ParsedCmd(cmd, args, redirectIn, redirectOut, appendOut, redirectErr, appendErr, background, display.toString());
    }

    static String findInPath(String cmd) {
        if (cmd.startsWith("/") || cmd.startsWith("./") || cmd.startsWith("../")) {
            File f = new File(cmd);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
            return null;
        }
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(":")) {
            File f = new File(dir, cmd);
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    static void reapCompletedJobs(PrintStream out) {
        if (JOBS.isEmpty()) return;
        settleJobs();

        int latestJob = -1;
        int previousJob = -1;
        for (Job job : JOBS) {
            previousJob = latestJob;
            latestJob = job.number();
        }

        List<Job> completedJobs = new ArrayList<>();
        for (Job job : JOBS) {
            if (!job.process().isAlive()) {
                String marker = job.number() == latestJob ? "+" : job.number() == previousJob ? "-" : " ";
                out.printf("[%d]%s  %-24s%s%n", job.number(), marker, "Done", job.command());
                completedJobs.add(job);
            }
        }
        JOBS.removeAll(completedJobs);
    }

    static void settleJobs() {
        try {
            Thread.sleep(75);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void executePipeline(List<List<Token>> pipeline) throws Exception {
        List<ParsedCmd> cmds = new ArrayList<>();
        for (var tokens : pipeline) cmds.add(parseCmd(tokens));

        int n = cmds.size();
        List<Process> processes = new ArrayList<>(Collections.nCopies(n, null));
        List<InputStream> prevOutputs = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            ParsedCmd pc = cmds.get(i);
            boolean last = (i == n - 1);
            boolean builtin = BUILTINS.contains(pc.cmd());

            // Determine stdin for this command
            InputStream stdin = System.in;
            if (i > 0) {
                stdin = prevOutputs.get(i - 1);
            }
            if (pc.redirectIn() != null) {
                stdin = new FileInputStream(pc.redirectIn());
            }

            if (builtin) {
                // Determine stdout/stderr for this builtin
                OutputStream stdout = System.out;
                OutputStream stderr = System.err;
                ByteArrayOutputStream pipeOut = null;

                List<Closeable> toClose = new ArrayList<>();
                if (pc.redirectIn() != null) {
                    toClose.add(stdin);
                }

                if (pc.redirectOut() != null) {
                    FileOutputStream fos = new FileOutputStream(pc.redirectOut(), pc.appendOut());
                    stdout = fos;
                    toClose.add(fos);
                } else if (!last) {
                    pipeOut = new ByteArrayOutputStream();
                    stdout = pipeOut;
                }

                if (pc.redirectErr() != null) {
                    FileOutputStream fos = new FileOutputStream(pc.redirectErr(), pc.appendErr());
                    stderr = fos;
                    toClose.add(fos);
                }

                try {
                    runBuiltin(pc, stdin, stdout, stderr);
                } finally {
                    for (Closeable c : toClose) {
                        try { c.close(); } catch (IOException e) {}
                    }
                }

                if (!last) {
                    if (pipeOut != null) {
                        prevOutputs.set(i, new ByteArrayInputStream(pipeOut.toByteArray()));
                    } else {
                        prevOutputs.set(i, new ByteArrayInputStream(new byte[0]));
                    }
                }
            } else {
                // External command
                String resolved = findInPath(pc.cmd());
                if (resolved == null) {
                    System.out.println(pc.cmd() + ": command not found");
                    if (!last) {
                        prevOutputs.set(i, new ByteArrayInputStream(new byte[0]));
                    }
                } else {
                    List<String> fullCmd = new ArrayList<>();
                    fullCmd.add(pc.cmd());
                    fullCmd.addAll(pc.args());

                    ProcessBuilder pb = new ProcessBuilder(fullCmd);

                    // If we have a file redirect for stdin, set it
                    if (pc.redirectIn() != null) {
                        pb.redirectInput(new File(pc.redirectIn()));
                    } else if (i == 0) {
                        // Inherit terminal stdin for the first process if not redirected
                        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                    }

                    // If last command, set up stdout/stderr redirections
                    if (pc.redirectOut() != null) {
                        if (pc.appendOut())
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(pc.redirectOut())));
                        else
                            pb.redirectOutput(ProcessBuilder.Redirect.to(new File(pc.redirectOut())));
                    } else if (last) {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    } else {
                        // Not last, output goes to pipe
                        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                    }

                    if (pc.redirectErr() != null) {
                        if (pc.appendErr())
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(pc.redirectErr())));
                        else
                            pb.redirectError(ProcessBuilder.Redirect.to(new File(pc.redirectErr())));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process p = pb.start();
                    processes.set(i, p);

                    if (pc.background() && last) {
                        int jobNumber = 1;
                        for (Job job : JOBS) {
                            if (job.number() >= jobNumber) {
                                jobNumber = job.number() + 1;
                            }
                        }
                        JOBS.add(new Job(jobNumber, p, pc.displayCommand()));
                        System.out.println("[" + jobNumber + "] " + p.pid());
                        continue;
                    }

                    if (!last) {
                        prevOutputs.set(i, p.getInputStream());
                    }
                }
            }
        }

        // Connect pipe sources to next process stdins
        for (int i = 1; i < n; i++) {
            Process nextProc = processes.get(i);
            if (nextProc != null) {
                InputStream src = prevOutputs.get(i - 1);
                if (src != null) {
                    final InputStream srcF = src;
                    final Process nextProcF = nextProc;
                    final int idx = i;
                    Thread connector = new Thread(() -> {
                        try (OutputStream os = nextProcF.getOutputStream()) {
                            byte[] buf = new byte[8192];
                            int read;
                            while ((read = srcF.read(buf)) != -1) {
                                os.write(buf, 0, read);
                                os.flush();
                            }
                        } catch (IOException e) {
                        } finally {
                            try { srcF.close(); } catch (IOException e) {}
                        }
                    });
                    connector.start();
                }
            }
        }

        // Only wait for the last foreground process; intermediate processes
        // will be terminated when their pipe breaks after the last one exits.
        for (int i = processes.size() - 1; i >= 0; i--) {
            Process p = processes.get(i);
            if (p != null && !cmds.get(i).background()) {
                p.waitFor();
                break;
            }
        }
        
        // If this pipeline is entirely foreground, destroy intermediate
        // processes and close pipe sources so connector threads can exit.
        boolean anyBackground = false;
        for (ParsedCmd cmd : cmds) {
            if (cmd.background()) { anyBackground = true; break; }
        }
        if (!anyBackground) {
            for (int i = 0; i < processes.size() - 1; i++) {
                Process p = processes.get(i);
                if (p != null) { p.destroy(); }
            }
            for (int i = 0; i < prevOutputs.size(); i++) {
                InputStream is = prevOutputs.get(i);
                if (is != null) {
                    try { is.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    static void runBuiltin(ParsedCmd pc, InputStream stdin, OutputStream stdout, OutputStream stderr) throws Exception {
        PrintStream out = new PrintStream(stdout, true);
        PrintStream err = new PrintStream(stderr, true);

        switch (pc.cmd()) {
            case "exit" -> {
                int code = 0;
                if (!pc.args().isEmpty()) {
                    try { code = Integer.parseInt(pc.args().get(0)); } catch (NumberFormatException e) {}
                }
                System.exit(code);
            }
            case "echo" -> {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < pc.args().size(); j++) {
                    if (j > 0) sb.append(' ');
                    sb.append(pc.args().get(j));
                }
                out.println(sb);
            }
            case "type" -> {
                if (!pc.args().isEmpty()) {
                    String cmd = pc.args().get(0);
                    if (BUILTINS.contains(cmd)) {
                        out.println(cmd + " is a shell builtin");
                    } else {
                        String path = findInPath(cmd);
                        if (path != null) {
                            out.println(cmd + " is " + path);
                        } else {
                            out.println(cmd + ": not found");
                        }
                    }
                }
            }
            case "pwd" -> out.println(System.getProperty("user.dir"));
            case "cd" -> {
                String dir;
                if (pc.args().isEmpty()) {
                    dir = System.getProperty("user.home");
                } else {
                    dir = pc.args().get(0);
                }
                File f = new File(dir);
                if (f.isAbsolute()) {
                    if (f.exists() && f.isDirectory()) {
                        System.setProperty("user.dir", f.getCanonicalPath());
                    } else {
                        err.println("cd: " + dir + ": No such file or directory");
                    }
                } else {
                    File cwdf = new File(System.getProperty("user.dir"), dir);
                    if (cwdf.exists() && cwdf.isDirectory()) {
                        System.setProperty("user.dir", cwdf.getCanonicalPath());
                    } else {
                        err.println("cd: " + dir + ": No such file or directory");
                    }
                }
            }
            case "jobs" -> {
                settleJobs();
                int latestJob = -1;
                int previousJob = -1;
                for (Job job : JOBS) {
                    previousJob = latestJob;
                    latestJob = job.number();
                }

                List<Job> completedJobs = new ArrayList<>();
                for (Job job : JOBS) {
                    String marker = job.number() == latestJob ? "+" : job.number() == previousJob ? "-" : " ";
                    if (job.process().isAlive()) {
                        out.printf("[%d]%s  %-24s%s &%n", job.number(), marker, "Running", job.command());
                    } else {
                        out.printf("[%d]%s  %-24s%s%n", job.number(), marker, "Done", job.command());
                        completedJobs.add(job);
                    }
                }
                JOBS.removeAll(completedJobs);
            }
        }
    }
}
