/** Logging functions.
 *  @version $Id$
 *  @author David Ripton
 */

public final class Log
{
    //    debug -- intended for developers only -- console or logfile
    //    info  -- routine info -- console or logfile or GUI scroll window
    //    warn  -- user mistake or important info -- message dialog
    //    error -- serious program error -- message dialog or stderr
    //    fatal -- fatal program error -- message dialog or stderr

    public static boolean DEBUG = true;

    /** Log an event. */
    public static void event(String s)
    {
        System.out.println(s);
    }

    /** Log an error. */
    public static void error(String s)
    {
        System.out.println("Error: " + s);
    }

    /** Log a warning. */
    public static void warn(String s)
    {
        System.out.println("Warn:" + s);
    }

    /** Log a debug message. */
    public static void debug(String s)
    {
        if (DEBUG)
        {
            System.out.println(s);
        }
    }
}
