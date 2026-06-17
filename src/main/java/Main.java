import java.util.Scanner;

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
                    System.out.println(commandToCheck + ": not found");
                }
            }else {
                System.out.println(input + ": command not found");
            }
        }
    }
}
