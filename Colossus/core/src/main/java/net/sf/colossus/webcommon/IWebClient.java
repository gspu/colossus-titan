package net.sf.colossus.webcommon;


/**
 *  Interface for what WebServer (Public Game Server) sends to WebClient
 *
 *  @author Clemens Katzer
 */
public interface IWebClient
{
    public static final String alreadyLoggedIn = "alreadyLoggedIn";
    public static final String grantAdmin = "grantAdmin";
    public static final String tooManyUsers = "tooManyUsers";
    public static final String connectionClosed = "connectionClosed";
    public static final String forcedLogout = "forcedLogout";
    public static final String didEnroll = "didEnroll";
    public static final String didUnenroll = "didUnenroll";
    public static final String gameInfo = "gameInfo";
    public static final String userInfo = "userInfo";
    public static final String gameStarted = "gameStarted";
    public static final String gameStartsNow = "gameStartsNow";
    public static final String gameStartsSoon = "gameStartsSoon";
    public static final String gameCancelled = "gameCancelled";
    public static final String chatDeliver = "chatDeliver";

    public void grantAdminStatus();

    public void didEnroll(String gameId, String username);

    public void didUnenroll(String gameId, String username);

    public void userInfo(int loggedin, int enrolled, int playing, int dead,
        long ago, String text);

    public void gameInfo(GameInfo gi);

    public void gameStartsNow(String gameId, int port, String hostingHost);

    public void gameStartsSoon(String gameId);

    public void gameStarted(String gameId);

    public void gameCancelled(String gameId, String byUser);

    public void chatDeliver(String chatId, long when, String sender,
        String message, boolean resent);
}
