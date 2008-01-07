package net.sf.colossus.webcommon;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;


/** One user at the WebServer side.
 *  Also used on client side, because interface requires so, but
 *  basically only to store the username, everything else is unused.
 *  
 *  The class statically contains a list of all user registered
 *  at the webserver; this list is read from a file (later a DB??)
 *  into a hashMap to quicky look up all users.
 *  
 *  @version $Id$
 *  @author Clemens Katzer
 */

public class User
{
    private static final Logger LOGGER =
        Logger.getLogger(User.class.getName());

    private static HashMap<String, User> userMap = new HashMap<String, User>();
    private static HashMap<String, User> loggedInUserMap = new HashMap<String, User>();

    private static String usersFile = null;

    private final static String typeUser = "user";
    private final static String typeAdmin = "admin";

    private static int maxUsers;

    private String name;
    private String password;
    private String email;
    private boolean isAdmin;
    private String created;

    private Thread thread;

    public User(String name)
    {
        this.name = name;
    }

    public User(String name, String password, String email, boolean isAdmin,
        String created)
    {
        this.name = name;
        this.password = password;
        this.email = email;
        this.isAdmin = isAdmin;
        this.created = created;
    }

    public String getName()
    {
        return this.name;
    }

    public String getEmail()
    {
        return email;
    }

    public boolean isAdmin()
    {
        return isAdmin;
    }

    public void setIsAdmin(boolean val)
    {
        isAdmin = val;
    }

    public Thread getThread()
    {
        return this.thread;
    }

    public void setProperties(String pw, String email, Boolean isAdminObj)
    {
        if (pw != null)
        {
            password = pw;
        }

        if (email != null)
        {
            this.email = email;
        }

        if (isAdminObj != null)
        {
            isAdmin = isAdminObj.booleanValue();
        }
    }

    public void setThread(Thread cst)
    {
        if (cst == null)
        {
            if (loggedInUserMap.containsKey(this.name))
            {
                loggedInUserMap.remove(this.name);
            }
        }
        else
        {
            loggedInUserMap.put(this.name, this);
        }
        this.thread = cst;
    }

    /*
     * Given a username and password, verifies that the user
     * is allowed to login with that password.
     * @param String username
     * @param String password
     * @returns String reasonLoginFailed, null if login ok
     **/

    public static String verifyLogin(String username, String password)
    {
        String reasonLoginFailed = null;

        User user = findUserByName(username);

        if (user == null)
        {
            reasonLoginFailed = "Invalid username";
        }
        else if (password != null && password.equals(user.password)
            && username.equals(user.name))
        {
            // ok, return null to indicate all is fine
        }
        else
        {
            reasonLoginFailed = "Invalid username/password";
        }

        return reasonLoginFailed;
    }

    public static void storeUser(User u)
    {
        String name = u.getName();
        String nameAllLower = name.toLowerCase();
        userMap.put(nameAllLower, u);
    }

    public static User findUserByName(String name)
    {
        String nameAllLower = name.toLowerCase();
        return userMap.get(nameAllLower);
    }

    public static Iterator<User> getLoggedInUsersIterator()
    {
        return loggedInUserMap.values().iterator();
    }

    public static String registerUser(String username, String password,
        String email, boolean isAdmin)
    {
        User alreadyExisting = findUserByName(username);
        if (alreadyExisting != null)
        {
            String problem = "User " + username + " does already exist.";
            return problem;
        }
        else if (userMap.size() >= maxUsers)
        {
            String problem = "Maximum number of accounts )" + maxUsers
                + ") reached - no more registrations possible,"
                + " until some administrator checks the situation.";
            return problem;
        }
        else
        {
            String created = makeCreatedDate(new Date().getTime());

            User u = new User(username, password, email, isAdmin, created);
            storeUser(u);
            storeUsersToFile();
            return null;
        }
    }

    public static String changeProperties(String username, String oldPW,
        String newPW, String email, Boolean isAdmin)
    {
        String reason;

        User u = findUserByName(username);
        if (u == null)
        {
            reason = "User does not exist";
            return reason;
        }
        else if ((reason = User.verifyLogin(username, oldPW)) != null)
        {
            return reason;
        }
        else
        {
            u.setProperties(newPW, email, isAdmin);
            storeUsersToFile();
            return null;
        }
    }

    public static final String CREATION_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private static SimpleDateFormat createdDateFormatter = new SimpleDateFormat(
        CREATION_FORMAT);

    private static String makeCreatedDate(long when)
    {
        Date whenDate = new Date(when);
        String whenString = createdDateFormatter.format(whenDate);
        return whenString;
    }

    public static void parseUserLine(String line)
    {
        String sep = net.sf.colossus.server.Constants.protocolTermSeparator;

        String[] tokens = line.split(new String(sep));
        if (tokens.length != 5)
        {
            LOGGER.log(Level.WARNING,
                "invalid line '" + line + "' in user file!");
            return;
        }
        String name = tokens[0].trim();
        String password = tokens[1].trim();
        String email = tokens[2].trim();
        String type = tokens[3].trim();
        String created = tokens[4].trim();
        boolean isAdmin = false;
        if (type.equals(typeAdmin))
        {
            isAdmin = true;
        }
        else if (type.equals(typeUser))
        {
            isAdmin = false;
        }
        else
        {
            LOGGER.log(Level.WARNING, "invalid type '" + type
                + "' in user file line '" + line + "'");
        }
        User u = new User(name, password, email, isAdmin, created);
        storeUser(u);
    }

    public static void readUsersFromFile(String filename, int maxUsersVal)
    {
        usersFile = filename;
        maxUsers = maxUsersVal;

        try
        {
            BufferedReader users = new BufferedReader(new InputStreamReader(
                new FileInputStream(filename)));

            String line = null;
            while ((line = users.readLine()) != null)
            {
                if (line.startsWith("#"))
                {
                    // ignore comment line
                }
                else if (line.matches(new String("\\s*")))
                {
                    // ignore empty line
                }
                else
                {
                    parseUserLine(line);
                }
            }
        }
        catch (FileNotFoundException e)
        {
            LOGGER.log(Level.SEVERE,
                "Users file " + filename + " not found!", e);
            System.exit(1);
        }
        catch (IOException e)
        {
            LOGGER.log(Level.SEVERE,
                "IOException while reading users file "
                + filename + "!", e);
            System.exit(1);
        }
    }

    public String makeLine()
    {
        String sep = net.sf.colossus.server.Constants.protocolTermSeparator;
        String type = (isAdmin ? typeAdmin : typeUser);

        String line = this.name + sep + password + sep + email + sep + type
            + sep + created;
        return line;
    }

    public static void storeUsersToFile()
    {
        String filename = usersFile;

        if (usersFile == null)
        {
            LOGGER.log(Level.SEVERE, "UsersFile name is null!");
            System.exit(1);
        }

        LOGGER.log(Level.FINE, "Storing users back to file " + filename);

        PrintWriter out = null;
        try
        {
            out = new PrintWriter(new FileOutputStream(filename));

            Iterator<String> it = userMap.keySet().iterator();
            while (it.hasNext())
            {
                String key = it.next();
                User user = userMap.get(key);
                String line = user.makeLine();
                out.println(line);
            }
            out.close();
        }
        catch (FileNotFoundException e)
        {
            LOGGER.log(Level.SEVERE, "Writing users file " + filename +
                "failed: FileNotFoundException: ", e);
            System.exit(1);
        }
    }

    public static void cleanup()
    {
        userMap.clear();
        loggedInUserMap.clear();
    }
}