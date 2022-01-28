package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.util.*;

import static java.lang.Integer.parseInt;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private Socket dictSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Status status;

    /**
     * Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        try {
            dictSocket = new Socket(host, port);
            out = new PrintWriter(dictSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(dictSocket.getInputStream()));
            status = Status.readStatus(in);
            System.out.println("Server: " + status.getStatusCode() + ": " + status.getDetails());
            if (!goodStatus()) {
                throw new DictConnectionException("Server response on connection: " + status.getDetails());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new DictConnectionException(e.getMessage());
        }
        // TODO Add your code here
    }

    /**
     * Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /**
     * Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     */
    public synchronized void close() {
        try {
            send("QUIT");
            out.close();
            in.close();
            dictSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // TODO Add your code here
    }

    /**
     * Requests and retrieves all definitions for a specific word.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        try {
            send("DEFINE " + database.getName() + " " + word);
            if (!goodStatus()) {
                return set;
            }

            int nDefinitions = parseInt(DictStringParser.splitAtoms(status.getDetails())[0]); // Number of definitions retrieved
            String fromServer;
            Definition definition = null;

            // Adding each definition to set
            while(set.size() < nDefinitions) {
                fromServer = in.readLine();
                String[] split = DictStringParser.splitAtoms(fromServer);
                // Case where this is end of definition
                 if (fromServer.equals(".")) {
                    set.add(definition);
                    continue;
                }
                // Case where this is a new definition
                else if (split.length > 0 && split[0].equals("151")) {
                    definition = new Definition(word, split[2]);
                }
                // Otherwise, add to current definition
                else {
                    if (definition != null) definition.appendDefinition(fromServer);
                }
            }
            in.readLine(); // Need this line to clear status message from the in BufferedReader
        } catch (Exception e) {
            e.printStackTrace();
            throw new DictConnectionException(e.getMessage());
        }

        // TODO Add your code here
        return set;
    }

    /**
     * Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();
        try {
            send("MATCH " + database.getName() + " " + strategy.getName() + " " + word);
            if (!goodStatus()) {
                return set;
            }

            // Add each line from response to set
            String fromServer = in.readLine();
            while (!fromServer.equals(".")) {
                set.add(DictStringParser.splitAtoms(fromServer)[1]);
                fromServer = in.readLine();
            }
            in.readLine(); // Need this line to clear status message from the in BufferedReader
        } catch (Exception e) {
            e.printStackTrace();
            throw new DictConnectionException(e.getMessage());
        }

        // TODO Add your code here
        return set;
    }

    /**
     * Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messagedon't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();
        try {
            send("SHOW DB");
            if (!goodStatus()) {
                return databaseMap;
            }

            // Parse each line into name and description, then add to databaseMap
            String fromServer = in.readLine();
            while (!fromServer.equals(".")) {
                String[] split = DictStringParser.splitAtoms(fromServer);
                String name =split[0];
                String description = String.join(" ", Arrays.copyOfRange(split, 1, split.length));
                databaseMap.put(name, new Database(name, description));
                fromServer = in.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new DictConnectionException(e.getMessage());
        }

        // TODO Add your code here
        return databaseMap;
    }

    /**
     * Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();
        try {
            send("SHOW STRATEGIES");
            if (!goodStatus()) {
                return set;
            }

            // Parse each line into "name" and "description", then add to the set
            String fromServer = in.readLine();
            while (!fromServer.equals(".")) {
                String name = fromServer.split(" ")[0],
                        description = fromServer.substring(name.length() + 1).replaceAll("\"", "");
                set.add(new MatchingStrategy(name, description));
                fromServer = in.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new DictConnectionException(e.getMessage());
        }

        // TODO Add your code here
        return set;
    }




    // Sends a message to server and logs the message and response to console
    private synchronized void send(String message) throws DictConnectionException {
        try {
            flushIn();
            out.println(message);
            System.out.println("Client: " + message);
            status = Status.readStatus(in);
            System.out.println("Server: " + status.getStatusCode() + ": " + status.getDetails());
        } catch (Exception e) {
            e.printStackTrace();
            throw new DictConnectionException(e.getMessage());
        }
    }

    // "Flushes" everything out of input buffer
    private synchronized void flushIn() throws DictConnectionException {
//        char[] cbuf = new char[8192];
//        try {
//            System.out.println("Flush: " + in.read(cbuf, 0, 8192));
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new DictConnectionException(e.getMessage());
//        }
        //TODO
    }

    // returns true if status code is "good" (1, 2, or 3)
    private synchronized boolean goodStatus() {
        return status.getStatusType() == Status.PRELIMINARY_REPLY || status.getStatusType() == Status.COMPLETION_REPLY || status.getStatusType() == Status.INTERMEDIATE_REPLY;
    }
}