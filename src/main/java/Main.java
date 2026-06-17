import java.util.Scanner;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        
        while(true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine();

            if(input.equals("exit")) {
                break;
            }else if(input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }else if(input.startsWith("type ")) {
                String commandToCheck = input.substring(5);
                
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || commandToCheck.equals("type")) {
                    System.out.println(commandToCheck + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    boolean found = false;

                    if(pathEnv != null) {
                        String[] directories = pathEnv.split(File.pathSeparator);
                    

                        for(String dir : directories) {
                            File file = new File(dir, commandToCheck);

                            if(file.exists() && file.canExecute()) {
                                System.out.println(commandToCheck + " is " + file.getAbsolutePath());
                                found = true;
                                break;
                            }
                        }
                    }

                    if(!found) {
                        System.out.println(commandToCheck + ": not found");
                    }
                }
            }else {
                System.out.println(input + ": command not found");
            }
        }
    }
}
