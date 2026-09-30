import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** A line-oriented, thread-per-client TCP chat server. */
public class ChatServer {
    private static final Map<String, ClientHandler> CLIENTS = new ConcurrentHashMap<>();
    private static final Map<String, String> PRIVATE_PARTNERS = new ConcurrentHashMap<>();
    private static final Set<String> BANNED = ConcurrentHashMap.newKeySet();
    private static final Object ROUTING_LOCK = new Object();
    private static String adminPassword = "";
    private static Path banFile = Paths.get("banned-users.txt");

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        adminPassword = args.length > 1 ? args[1] : System.getenv().getOrDefault("CHAT_ADMIN_PASSWORD", "");
        if (Files.exists(banFile)) BANNED.addAll(Files.readAllLines(banFile, StandardCharsets.UTF_8));
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Chat server listening on port " + port);
            while (true) {
                Socket socket = server.accept();
                Thread thread = new Thread(new ClientHandler(socket), "chat-client-" + socket.getPort());
                thread.start();
            }
        }
    }

    private static void broadcast(String message, ClientHandler except) {
        for (ClientHandler client : CLIENTS.values()) if (client != except) client.send(message);
    }

    private static void saveBans() {
        try { Files.write(banFile, new TreeSet<>(BANNED), StandardCharsets.UTF_8); }
        catch (IOException e) { System.err.println("Could not save ban list: " + e.getMessage()); }
    }

    private static final class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private String username;
        private boolean superUser;

        ClientHandler(Socket socket) { this.socket = socket; }

        synchronized void send(String message) {
            if (out != null) { out.println(message); if (out.checkError()) close(); }
        }

        @Override public void run() {
            boolean announced = false;
            try (Socket ignored = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                send("Welcome. Login with: username [admin-password]");
                String login = in.readLine();
                if (login == null) return;
                String[] parts = login.trim().split("\\s+", 2);
                if (parts.length == 0 || !parts[0].matches("[A-Za-z0-9_-]{1,24}")) { send("ERROR Invalid username (use 1-24 letters, digits, _ or -)."); return; }
                username = parts[0];
                synchronized (ROUTING_LOCK) {
                    if (BANNED.contains(username)) { send("ERROR This username is permanently banned."); return; }
                    if (CLIENTS.putIfAbsent(username, this) != null) { send("ERROR Username is already connected."); return; }
                    superUser = !adminPassword.isEmpty() && parts.length == 2 && adminPassword.equals(parts[1]);
                    broadcast("*** " + username + " joined" + (superUser ? " (super user)" : "") + " ***", this);
                    send("OK Connected as " + username + (superUser ? " (super user)" : " (normal user)"));
                    send("Commands: /private USER, /public, /ban USER (super users), /quit");
                    announced = true;
                }
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("/quit")) break;
                    if (line.startsWith("/private ")) { setPrivate(line.substring(9).trim()); continue; }
                    if (line.equalsIgnoreCase("/public")) { leavePrivate(); continue; }
                    if (line.startsWith("/ban ")) { ban(line.substring(5).trim()); continue; }
                    if (line.startsWith("/")) { send("ERROR Unknown command."); continue; }
                    if (line.trim().isEmpty()) continue;
                    deliver("[" + username + "] " + line);
                }
            } catch (IOException e) {
                if (username != null) System.out.println(username + " disconnected: " + e.getMessage());
            } finally {
                if (username != null) {
                    synchronized (ROUTING_LOCK) {
                        CLIENTS.remove(username, this);
                        removePrivatePair(username);
                        if (announced) broadcast("*** " + username + " left ***", this);
                    }
                }
                close();
            }
        }

        private void deliver(String message) {
            synchronized (ROUTING_LOCK) {
                String partner = PRIVATE_PARTNERS.get(username);
                if (partner == null) {
                    // Public messages are visible to everyone, including private-pair members.
                    broadcast(message, this);
                } else {
                    ClientHandler p = CLIENTS.get(partner);
                    if (p != null) p.send(message);
                }
            }
        }

        private void setPrivate(String target) {
            synchronized (ROUTING_LOCK) {
                ClientHandler other = CLIENTS.get(target);
                if (target.equals(username) || other == null) { send("ERROR User is not available for a private channel."); return; }
                if (PRIVATE_PARTNERS.containsKey(username) || PRIVATE_PARTNERS.containsKey(target)) { send("ERROR One user is already in a private channel."); return; }
                PRIVATE_PARTNERS.put(username, target); PRIVATE_PARTNERS.put(target, username);
                send("Private channel opened with " + target + ". You can still see public messages.");
                other.send(username + " opened a private channel with you. You can still see public messages.");
                for (ClientHandler c : CLIENTS.values()) if (c != this && c != other) c.send("*** A private channel was opened by two users. ***");
            }
        }

        private void leavePrivate() {
            synchronized (ROUTING_LOCK) {
                String partner = PRIVATE_PARTNERS.get(username);
                if (partner == null) { send("You are already on the public channel."); return; }
                removePrivatePair(username);
                send("Returned to the public channel.");
                ClientHandler p = CLIENTS.get(partner);
                if (p != null) p.send(username + " closed the private channel.");
                broadcast("*** A private channel was closed. ***", this);
            }
        }

        private void removePrivatePair(String name) {
            String partner = PRIVATE_PARTNERS.remove(name);
            if (partner != null) PRIVATE_PARTNERS.remove(partner, name);
        }

        private void ban(String target) {
            synchronized (ROUTING_LOCK) {
                if (!superUser) { send("ERROR Only a super user can ban users."); return; }
                if (!target.matches("[A-Za-z0-9_-]{1,24}")) { send("ERROR Invalid username."); return; }
                if (target.equals(username)) { send("ERROR You cannot ban yourself."); return; }
                if (BANNED.add(target)) saveBans();
                ClientHandler victim = CLIENTS.get(target);
                if (victim != null) { victim.send("You have been permanently banned."); victim.close(); }
                broadcast("*** " + target + " was permanently banned by " + username + " ***", this);
                send("User " + target + " is permanently banned.");
            }
        }

        private synchronized void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
