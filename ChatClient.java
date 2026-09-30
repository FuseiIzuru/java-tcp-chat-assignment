import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Console client. A background reader prints server messages while stdin is read. */
public class ChatClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        try (Socket socket = new Socket(host, port);
             BufferedReader server = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            Thread receiver = new Thread(() -> {
                try { String message; while ((message = server.readLine()) != null) System.out.println(message); }
                catch (IOException e) { System.out.println("Connection closed."); }
            }, "chat-server-reader");
            receiver.setDaemon(true);
            receiver.start();
            System.out.print("Username (append admin password if you are a super user): ");
            String login = console.readLine();
            if (login == null) return;
            out.println(login);
            String line;
            while ((line = console.readLine()) != null) {
                out.println(line);
                if (line.equalsIgnoreCase("/quit")) break;
            }
        } catch (IOException e) {
            System.err.println("Could not connect: " + e.getMessage());
        }
    }
}
