package net.sf.colossus.gui;



import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.colossus.client.Client;
import net.sf.colossus.client.LegionClientSide;
import net.sf.colossus.common.Constants;
import net.sf.colossus.common.Options;
import net.sf.colossus.game.Legion;
import net.sf.colossus.game.Phase;
import net.sf.colossus.game.Player;
import net.sf.colossus.game.PlayerColor;
import net.sf.colossus.guiutil.KFrame;
import net.sf.colossus.guiutil.SaveWindow;
import net.sf.colossus.server.XMLSnapshotFilter;
import net.sf.colossus.util.ArrayHelper;
import net.sf.colossus.util.BuildInfo;
import net.sf.colossus.util.HTMLColor;
import net.sf.colossus.util.NullCheckPredicate;
import net.sf.colossus.util.StaticResourceLoader;
import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.MasterHex;
import net.sf.colossus.variant.Variant;


/**
 * Class MasterBoard implements the GUI for a Titan masterboard.
 *
 * @author David Ripton
 * @author Romain Dolbeau
 */
public final class MasterBoard extends JPanel
{
    private static final Logger LOGGER = Logger.getLogger(MasterBoard.class
        .getName());

    private Image offScreenBuffer;
    private boolean overlayChanged = false;

    private GUIMasterHex[][] guiHexArray = null;

    private Client client;
    private final ClientGUI gui;

    private KFrame masterFrame;
    private ShowReadme showReadme;
    private ShowHelpDoc showHelpDoc;
    private JMenu phaseMenu;
    private JPopupMenu popupMenu;
    private Map<String, JCheckBoxMenuItem> checkboxes = new HashMap<String, JCheckBoxMenuItem>();
    private JPanel[] legionFlyouts;

    private final MasterBoardWindowHandler mbwh;
    private InfoPopupHandler iph;

    /** Last point clicked is needed for popup menus. */
    private Point lastPoint;

    /**
     *  List of markers which are currently on the board,
     *  for painting in z-order => the end of the list is on top.
     *
     *  Now synchronized access to prevent NPEs when EDT wants to
     *  paint a marker and asks for the legion for it, and
     *  legion has just been removed.
     *  I don't use a synchronizedList, because then I get into
     *  trouble in the recreateMarkers method.
     */
    private final LinkedHashMap<Legion, Marker> legionToMarkerMap = new LinkedHashMap<Legion, Marker>();

    private final Map<Legion, Chit> recruitedChits = new HashMap<Legion, Chit>();
    private final Map<MasterHex, List<Chit>> possibleRecruitChits = new HashMap<MasterHex, List<Chit>>();

    /** The scrollbarspanel, needed to correct lastPoint. */
    private JScrollPane scrollPane;

    private final Container contentPane;

    /** our own little bar implementation */
    private BottomBar bottomBar;
    private boolean gameOverStateReached = false;

    private static final String saveGameAs = "Save game as";

    private static final String clearRecruitChits = "Clear recruit chits";

    private static final String undoLast = "Undo";
    private static final String undoAll = "Undo All";
    private static final String doneWithPhase = "Done";
    private static final String forcedDoneWithPhase = "Forced Done";

    private static final String takeMulligan = "Take Mulligan";
    private static final String withdrawFromGame = "Withdraw from Game";

    private static final String viewWebClient = "View Web Client";
    private static final String viewFullRecruitTree = "View Full Recruit Tree";
    private static final String viewHexRecruitTree = "View Hex Recruit Tree";
    private static final String viewBattleMap = "View Battle Map";

    private static final String chooseScreen = "Choose Screen For Info Windows";
    private static final String preferences = "Preferences...";

    private static final String about = "About";
    private static final String viewReadme = "Show Variant Readme";
    private static final String viewHelpDoc = "Options Documentation";

    private AbstractAction newGameAction;
    private AbstractAction loadGameAction;
    private AbstractAction saveGameAction;
    private AbstractAction saveGameAsAction;
    private AbstractAction closeBoardAction;
    private AbstractAction quitGameAction;
    private AbstractAction checkConnectionAction;

    private AbstractAction clearRecruitChitsAction;
    private AbstractAction undoLastAction;
    private AbstractAction undoAllAction;
    private AbstractAction doneWithPhaseAction;
    private AbstractAction forcedDoneWithPhaseAction;
    private AbstractAction takeMulliganAction;
    private AbstractAction withdrawFromGameAction;

    private AbstractAction viewWebClientAction;
    private AbstractAction viewFullRecruitTreeAction;
    private AbstractAction viewHexRecruitTreeAction;
    private AbstractAction viewBattleMapAction;

    private AbstractAction chooseScreenAction;

    private AbstractAction preferencesAction;

    private AbstractAction aboutAction;
    private AbstractAction viewReadmeAction;
    private AbstractAction viewHelpDocAction;

    private boolean playerLabelDone;

    private SaveWindow saveWindow;

    private String cachedPlayerName = "<not set yet>";

    private final class InfoPopupHandler extends KeyAdapter
    {
        private static final int POPUP_KEY_ALL_LEGIONS = KeyEvent.VK_SHIFT;
        private static final int POPUP_KEY_MY_LEGIONS = KeyEvent.VK_CONTROL;
        private static final int PANEL_MARGIN = 4;
        private static final int PANEL_PADDING = 0;

        private final WeakReference<Client> clientRef;

        private InfoPopupHandler(Client client)
        {
            super();
            this.clientRef = new WeakReference<Client>(client);
            net.sf.colossus.util.InstanceTracker.register(this, gui
                .getOwningPlayer().getName());
        }

        @Override
        public void keyPressed(KeyEvent e)
        {
            Client client = clientRef.get();
            if (client == null)
            {
                return;
            }
            if (e.getKeyCode() == POPUP_KEY_ALL_LEGIONS)
            {
                if (legionFlyouts == null)
                {
                    synchronized (legionToMarkerMap)
                    {
                        createLegionFlyouts(legionToMarkerMap.values());
                    }
                }
            }
            else if (e.getKeyCode() == POPUP_KEY_MY_LEGIONS)
            {
                if (legionFlyouts == null)
                {
                    // copy only local players markers
                    List<Marker> myMarkers = new ArrayList<Marker>();
                    synchronized (legionToMarkerMap)
                    {
                        for (Entry<Legion, Marker> entry : legionToMarkerMap
                            .entrySet())
                        {
                            Legion legion = entry.getKey();
                            if (client.isMyLegion(legion))
                            {
                                myMarkers.add(entry.getValue());
                            }
                        }
                        createLegionFlyouts(myMarkers);
                    }
                }
            }
            else
            {
                super.keyPressed(e);
            }
        }

        private void createLegionFlyouts(Collection<Marker> markers)
        {
            // copy to array so we don't get concurrent modification
            // exceptions when iterating
            Marker[] markerArray = markers.toArray(new Marker[markers.size()]);
            legionFlyouts = new JPanel[markers.size()];
            for (int i = 0; i < markerArray.length; i++)
            {
                Marker marker = markerArray[i];
                LegionClientSide legion = client.getLegion(marker.getId());
                int scale = 2 * Scale.get();

                boolean dubiousAsBlanks = gui.getOptions().getOption(
                    Options.dubiousAsBlanks);
                final JPanel panel = new LegionInfoPanel(legion, scale,
                    PANEL_MARGIN, PANEL_PADDING, true, gui.getViewMode(),
                    client.isMyLegion(legion),
                    dubiousAsBlanks, true);
                add(panel);
                legionFlyouts[i] = panel;

                panel.setLocation(marker.getLocation());
                panel.setVisible(true);
                DragListener.makeDraggable(panel);

                repaint();
            }
        }

        @Override
        public void keyReleased(KeyEvent e)
        {
            if ((e.getKeyCode() == POPUP_KEY_ALL_LEGIONS)
                || (e.getKeyCode() == POPUP_KEY_MY_LEGIONS))
            {
                if (legionFlyouts != null)
                {
                    for (JPanel flyout : legionFlyouts)
                    {
                        remove(flyout);
                    }
                    repaint();
                    legionFlyouts = null;
                }
            }
            else
            {
                super.keyReleased(e);
            }
        }
    }

    public MasterBoard(final Client client, ClientGUI gui)
    {
        setLayout(null);
        this.client = client;
        this.gui = gui;

        net.sf.colossus.util.InstanceTracker.register(this, gui
            .getOwningPlayerName());

        String playerName = gui.getOwningPlayerName();
        if (playerName == null)
        {
            playerName = "unknown";
        }
        masterFrame = new KFrame("MasterBoard " + playerName);
        masterFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        contentPane = masterFrame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        setOpaque(true);
        setupIcon();
        setBackground(Color.black);
        this.mbwh = new MasterBoardWindowHandler();
        masterFrame.addWindowListener(mbwh);
        addMouseListener(new MasterBoardMouseHandler());
        addMouseMotionListener(new MasterBoardMouseMotionHandler());
        this.iph = new InfoPopupHandler(client);
        masterFrame.addKeyListener(this.iph);

        setupGUIHexes();
        setupActions();
        setupPopupMenu();
        setupTopMenu();

        scrollPane = new JScrollPane(this);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        setupPlayerLabel();

        saveWindow = new SaveWindow(gui.getOptions(), "MasterBoardScreen");
        Point loadLocation = saveWindow.loadLocation();

        if (loadLocation == null)
        {
            // Copy of code from KDialog.centerOnScreen();
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            masterFrame.setLocation(new Point(d.width / 2 - getSize().width
                / 2, d.height / 2 - getSize().height / 2));
        }
        else
        {
            masterFrame.setLocation(loadLocation);
        }

        masterFrame.pack();
        masterFrame.setVisible(true);
    }

    // For HotSeatMode
    public void setBoardActive(boolean val)
    {
        if (val)
        {
            masterFrame.setExtendedState(JFrame.NORMAL);
            masterFrame.repaint();
            reqFocus();
        }
        else
        {
            masterFrame.setExtendedState(JFrame.ICONIFIED);
        }
    }

    /**
     * Decide whether game is currently in a state that saving is currently
     * possible / reliable, i.e. reloading it would result in a game that can
     * be continued.
     * Saving is currently not reliable while any engagement is ongoing
     * (both negotiation and battle phases, up to summon/acquire.
     * Only just before any new engagement is picked (or waiting for
     * Done with Engagements) is currently (2009-07) working.
     *
     * @return True if saving at this point is fully reliable
     */
    private boolean isSavingCurrentlyUseful()
    {
        if (gui.getGame().isEngagementOngoing())
        {
            return false;
        }
        return true;
    }

    /**
     * Ask user in a dialog box, whether he wants to save game despite the
     * fact that game is currently in a phase for which loading the saved game
     * might not be properly loadable (during an engagement, for example)
     *
     * @return True if saving shall be done anyway
     */
    private boolean saveAnywayDialog()
    {
        String[] options = new String[2];
        options[0] = "OK";
        options[1] = "Cancel";
        int answer = JOptionPane.showOptionDialog(masterFrame,
            "Saving at this moment will not result in a consistent / properly"
                + " loadable save game file. Do you want to save anyway?",
            "Save at this phase not implemented!",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
            options, options[1]);

        return (answer == JOptionPane.OK_OPTION);
    }

    private void setupActions()
    {
        clearRecruitChitsAction = new AbstractAction(clearRecruitChits)
        {
            public void actionPerformed(ActionEvent e)
            {
                clearRecruitedChits();
                clearPossibleRecruitChits();
                // TODO Only repaint needed hexes.
                repaint();
            }
        };

        undoLastAction = new AbstractAction(undoLast)
        {
            public void actionPerformed(ActionEvent e)
            {
                Phase phase = client.getPhase();
                if (phase == Phase.SPLIT)
                {
                    gui.undoLastSplit();
                    alignAllLegions();
                    highlightTallLegions();
                    repaint();
                }
                else if (phase == Phase.MOVE)
                {
                    clearRecruitedChits();
                    clearPossibleRecruitChits();
                    gui.undoLastMove();
                    highlightUnmovedLegions();
                }
                else if (phase == Phase.FIGHT)
                {
                    LOGGER.log(Level.SEVERE, "called undoLastAction in FIGHT");
                }
                else if (phase == Phase.MUSTER)
                {
                    gui.undoLastRecruit();
                    highlightPossibleRecruitLegionHexes();
                }
                else
                {
                    LOGGER.log(Level.SEVERE, "Bogus phase");
                }
            }
        };

        undoAllAction = new AbstractAction(undoAll)
        {
            public void actionPerformed(ActionEvent e)
            {
                Phase phase = client.getPhase();
                if (phase == Phase.SPLIT)
                {
                    gui.undoAllSplits();
                    alignAllLegions();
                    highlightTallLegions();
                    repaint();
                }
                else if (phase == Phase.MOVE)
                {
                    gui.undoAllMoves();
                    highlightUnmovedLegions();
                }
                else if (phase == Phase.FIGHT)
                {
                    LOGGER.log(Level.SEVERE, "called undoAllAction in FIGHT");
                }
                else if (phase == Phase.MUSTER)
                {
                    gui.undoAllRecruits();
                    highlightPossibleRecruitLegionHexes();
                }
                else
                {
                    LOGGER.log(Level.SEVERE, "Bogus phase");
                }
            }
        };

        doneWithPhaseAction = new AbstractAction(doneWithPhase)
        {
            public void actionPerformed(ActionEvent e)
            {
                if (gameOverStateReached)
                {
                    gui.askNewCloseQuitCancel(masterFrame, false);
                }
                else
                {
                    // first set disabled...
                    doneWithPhaseAction.setEnabled(false);
                    // ... because response from server might set it
                    // to enabled again
                    client.doneWithPhase();
                }
            }
        };
        // will be enabled if it is player's turn
        doneWithPhaseAction.setEnabled(false);

        forcedDoneWithPhaseAction = new AbstractAction(forcedDoneWithPhase)
        {
            public void actionPerformed(ActionEvent e)
            {
                client.doneWithPhase();
            }
        };
        // make this always be available
        forcedDoneWithPhaseAction.setEnabled(true);

        takeMulliganAction = new AbstractAction(takeMulligan)
        {
            public void actionPerformed(ActionEvent e)
            {
                client.mulligan();
            }
        };

        withdrawFromGameAction = new AbstractAction(withdrawFromGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                String[] options = new String[2];
                options[0] = "Yes";
                options[1] = "No";
                int answer = JOptionPane.showOptionDialog(masterFrame,
                    "Are you sure you wish to withdraw from the game?",
                    "Confirm Withdrawal?", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

                if (answer == JOptionPane.YES_OPTION)
                {
                    client.withdrawFromGame();
                }
            }
        };

        viewFullRecruitTreeAction = new AbstractAction(viewFullRecruitTree)
        {
            public void actionPerformed(ActionEvent e)
            {
                Variant variant = gui.getGame().getVariant();
                new ShowAllRecruits(masterFrame, gui.getOptions(), variant,
                    gui);
            }
        };

        viewWebClientAction = new AbstractAction(viewWebClient)
        {
            public void actionPerformed(ActionEvent e)
            {
                gui.showWebClient();
            }
        };

        viewHexRecruitTreeAction = new AbstractAction(viewHexRecruitTree)
        {
            public void actionPerformed(ActionEvent e)
            {
                GUIMasterHex hex = getHexContainingPoint(lastPoint);
                if (hex != null)
                {
                    // TODO replace with actual ...getVariant() when Variant is
                    //      ready to provide the needed data
                    Variant variant = gui.getGame().getVariant();
                    MasterHex hexModel = hex.getHexModel();
                    new ShowRecruits(masterFrame, lastPoint, hexModel,
                        scrollPane, variant, gui);
                }
            }
        };

        viewBattleMapAction = new AbstractAction(viewBattleMap)
        {
            public void actionPerformed(ActionEvent e)
            {
                GUIMasterHex hex = getHexContainingPoint(lastPoint);
                if (hex != null)
                {
                    new ShowBattleMap(masterFrame, gui, hex);
                }
            }
        };

        /*
         * After confirmation (if necessary, i.e. not gameover yet),
         * totally quit everything (shut down server and all windows)
         * so that the ViableEntityManager knows it can let the main
         * go to the end, ending the JVM.
         */
        quitGameAction = new AbstractAction(Constants.quitGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                boolean quitAll = false;
                if (gui.getGame().isGameOver())
                {
                    quitAll = true;
                }
                else
                {
                    String[] options = new String[2];
                    options[0] = "Yes";
                    options[1] = "No";
                    int answer = JOptionPane.showOptionDialog(masterFrame,
                        "Are you sure you wish to stop this game and quit?",
                        "Quit Game?", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, options,
                        options[1]);
                    if (answer == JOptionPane.YES_OPTION)
                    {
                        quitAll = true;
                    }
                }

                if (quitAll)
                {
                    // In startObject, set up what to do next
                    gui.menuQuitGame();
                }
            }
        };

        newGameAction = new AbstractAction(Constants.newGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                if (!gui.getGame().isGameOver())
                {
                    String[] options = new String[2];
                    options[0] = "Yes";
                    options[1] = "No";
                    int answer = JOptionPane.showOptionDialog(masterFrame,
                        "Are you sure you want to stop this game and "
                            + "start a new one?", "New Game?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, options,
                        options[1]);

                    if (answer != JOptionPane.YES_OPTION)
                    {
                        return;
                    }
                }
                gui.menuNewGame();
            }
        };

        loadGameAction = new AbstractAction(Constants.loadGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                // No need for confirmation because the user can cancel
                // from the load game dialog.
                JFileChooser chooser = new JFileChooser(
                    Constants.SAVE_DIR_NAME);
                chooser.setFileFilter(new XMLSnapshotFilter());
                int returnVal = chooser.showOpenDialog(masterFrame);
                if (returnVal == JFileChooser.APPROVE_OPTION)
                {
                    gui.menuLoadGame(chooser.getSelectedFile().getPath());
                }
            }
        };

        saveGameAction = new AbstractAction(Constants.saveGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                boolean proceed = true;
                if (!isSavingCurrentlyUseful())
                {
                    proceed = saveAnywayDialog();
                }
                if (proceed)
                {
                    gui.menuSaveGame(null);
                }

            }
        };

        saveGameAsAction = new AbstractAction(saveGameAs)
        {
            // TODO: Need a confirmation dialog on overwrite?
            public void actionPerformed(ActionEvent e)
            {
                if (!isSavingCurrentlyUseful())
                {
                    boolean proceed = saveAnywayDialog();
                    if (!proceed)
                    {
                        return;
                    }
                }

                File savesDir = new File(Constants.SAVE_DIR_NAME);
                if (!savesDir.exists())
                {
                    LOGGER.log(Level.INFO, "Trying to make directory "
                        + savesDir.toString());
                    if (!savesDir.mkdirs())
                    {
                        LOGGER.log(Level.SEVERE,
                            "Could not create saves directory");
                        JOptionPane
                            .showMessageDialog(
                                masterFrame,
                                "Could not create directory "
                                    + savesDir
                                    + "\n- FileChooser dialog box will default "
                                    + "to some other (system dependent) directory!",
                                "Creating directory " + savesDir + " failed!",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
                else if (!savesDir.isDirectory())
                {
                    LOGGER.log(Level.SEVERE, "Can't create directory "
                        + savesDir.toString()
                        + " - name exists but is not a file");
                    JOptionPane.showMessageDialog(masterFrame,
                        "Can't create directory " + savesDir
                            + " (name exists, but is not a file)\n"
                            + "- FileChooser dialog box will default to "
                            + "some other (system dependent) directory!",
                        "Creating directory " + savesDir + " failed!",
                        JOptionPane.ERROR_MESSAGE);
                }

                JFileChooser chooser = new JFileChooser(savesDir);
                chooser.setFileFilter(new XMLSnapshotFilter());
                int returnVal = chooser.showSaveDialog(masterFrame);
                if (returnVal == JFileChooser.APPROVE_OPTION)
                {
                    String dirname = chooser.getCurrentDirectory()
                        .getAbsolutePath();
                    String basename = chooser.getSelectedFile().getName();
                    // Add default savegame extension.
                    if (!basename.endsWith(Constants.XML_EXTENSION))
                    {
                        basename += Constants.XML_EXTENSION;
                    }
                    gui.menuSaveGame(dirname + '/' + basename);
                }
            }
        };

        /*
         * after confirmation, close board and perhaps battle board, but
         * Webclient (and server, if running here), will go on.
         */
        closeBoardAction = new AbstractAction(Constants.closeBoard)
        {
            public void actionPerformed(ActionEvent e)
            {
                boolean closeBoard = false;
                if (gui.getGame().isGameOver() || !client.isAlive())
                {
                    closeBoard = true;
                }
                else
                {
                    String[] options = new String[2];
                    options[0] = "Yes";
                    options[1] = "No";
                    int answer = JOptionPane
                        .showOptionDialog(
                            masterFrame,
                            "Are you sure you wish to withdraw and close the board?",
                            "Close Board?", JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE, null, options,
                            options[1]);
                    if (answer == JOptionPane.YES_OPTION)
                    {
                        closeBoard = true;
                    }
                }

                if (closeBoard)
                {
                    gui.menuCloseBoard();
                }
            }
        };

        checkConnectionAction = new AbstractAction(Constants.checkConnection)
        {
            public void actionPerformed(ActionEvent e)
            {
                gui.checkServerConnection();
            }
        };

        chooseScreenAction = new AbstractAction(chooseScreen)
        {
            public void actionPerformed(ActionEvent e)
            {
                new ChooseScreen(getFrame(), gui);
            }
        };

        preferencesAction = new AbstractAction(preferences)
        {
            public void actionPerformed(ActionEvent e)
            {
                gui.setPreferencesWindowVisible(true);
            }
        };

        aboutAction = new AbstractAction(about)
        {
            public void actionPerformed(ActionEvent e)
            {
                String buildInfo = BuildInfo.getFullBuildInfoString() + "\n"
                    + "user.home:      " + System.getProperty("user.home");
                String colossusHome = System.getProperty("user.home")
                    + File.separator + ".colossus";
                String logDirectory = getLogDirectory();

                JOptionPane.showMessageDialog(masterFrame, ""
                    + "Colossus build: " + buildInfo + "\n"
                    + "Colossus home:  " + colossusHome + "\n"
                    + "Log directory:  " + logDirectory + "\n"
                    + "java.version:   " + System.getProperty("java.version"),
                    "About Colossus",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };

        viewReadmeAction = new AbstractAction(viewReadme)
        {
            public void actionPerformed(ActionEvent e)
            {
                if (showReadme != null)
                {
                    showReadme.dispose();
                }
                showReadme = new ShowReadme(gui.getGame().getVariant());
            }
        };
        viewHelpDocAction = new AbstractAction(viewHelpDoc)
        {
            public void actionPerformed(ActionEvent e)
            {
                if (showHelpDoc != null)
                {
                    showHelpDoc.dispose();
                }
                showHelpDoc = new ShowHelpDoc();
            }
        };
    }

    // Derive the logDirectory from the FileHandler.pattern property
    // (in case someone modified the default in logging.properties file)
    // to show it in Help-About dialog.
    private String getLogDirectory()
    {
        String propName = "java.util.logging.FileHandler.pattern";
        String logPattern = LogManager.getLogManager().getProperty(propName);
        String logDirectory = logPattern;
        try
        {
            File logFileWouldBe = new File(logPattern);
            logDirectory = logFileWouldBe.getParent();
        }
        catch (Exception ex)
        {
            LOGGER.warning("Exception while trying to determine log "
                + "directory from logPattern " + logPattern
                + " - ignoring it.");
        }

        // initialize logPath with what we have so far...
        String logPath = logDirectory;

        // ... and try to resolve "DOCUME~1" stuff to real names on windows:
        try
        {
            File logDir = new File(logDirectory);
            logPath = logDir.getCanonicalPath();
        }
        catch (Exception exc)
        {
            // ignore it...
        }
        return logPath;
    }

    public void doQuitGameAction()
    {
        quitGameAction.actionPerformed(null);
    }

    private void setupPopupMenu()
    {
        popupMenu = new JPopupMenu();
        contentPane.add(popupMenu);

        JMenuItem mi = popupMenu.add(viewHexRecruitTreeAction);
        mi.setMnemonic(KeyEvent.VK_R);

        mi = popupMenu.add(viewBattleMapAction);
        mi.setMnemonic(KeyEvent.VK_B);
    }

    private ItemListener itemHandler = new MasterBoardItemHandler();

    private JCheckBoxMenuItem addCheckBox(JMenu menu, String name, int mnemonic)
    {
        JCheckBoxMenuItem cbmi = new JCheckBoxMenuItem(name);
        cbmi.setMnemonic(mnemonic);
        cbmi.setSelected(gui.getOptions().getOption(name));

        cbmi.addItemListener(itemHandler);
        menu.add(cbmi);
        checkboxes.put(name, cbmi);
        return cbmi;
    }

    private void cleanCBListeners()
    {
        Iterator<String> it = checkboxes.keySet().iterator();
        while (it.hasNext())
        {
            String key = it.next();
            JCheckBoxMenuItem cbmi = checkboxes.get(key);
            cbmi.removeItemListener(itemHandler);
        }
        checkboxes.clear();
        checkboxes = null;
        itemHandler = null;
    }

    private void setupTopMenu()
    {
        JMenuBar menuBar = new JMenuBar();
        masterFrame.setJMenuBar(menuBar);

        // File menu

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(fileMenu);
        JMenuItem mi;

        mi = fileMenu.add(newGameAction);
        mi.setMnemonic(KeyEvent.VK_N);
        if (!client.isRemote())
        {
            mi = fileMenu.add(loadGameAction);
            mi.setMnemonic(KeyEvent.VK_L);
            mi = fileMenu.add(saveGameAction);
            mi.setMnemonic(KeyEvent.VK_S);
            mi = fileMenu.add(saveGameAsAction);
            mi.setMnemonic(KeyEvent.VK_A);
        }
        fileMenu.addSeparator();

        mi = fileMenu.add(checkConnectionAction);
        mi.setMnemonic(KeyEvent.VK_K);
        fileMenu.addSeparator();

        mi = fileMenu.add(closeBoardAction);
        mi.setMnemonic(KeyEvent.VK_C);
        mi = fileMenu.add(quitGameAction);
        mi.setMnemonic(KeyEvent.VK_Q);

        // Phase menu

        phaseMenu = new JMenu("Phase");
        phaseMenu.setMnemonic(KeyEvent.VK_P);
        menuBar.add(phaseMenu);

        mi = phaseMenu.add(clearRecruitChitsAction);
        mi.setMnemonic(KeyEvent.VK_C);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0));

        phaseMenu.addSeparator();

        mi = phaseMenu.add(undoLastAction);
        mi.setMnemonic(KeyEvent.VK_U);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, 0));

        mi = phaseMenu.add(undoAllAction);
        mi.setMnemonic(KeyEvent.VK_A);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0));

        mi = phaseMenu.add(doneWithPhaseAction);
        mi.setMnemonic(KeyEvent.VK_D);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));

        mi = phaseMenu.add(forcedDoneWithPhaseAction);
        mi.setMnemonic(KeyEvent.VK_F);

        phaseMenu.addSeparator();

        mi = phaseMenu.add(takeMulliganAction);
        mi.setMnemonic(KeyEvent.VK_M);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0));

        phaseMenu.addSeparator();

        mi = phaseMenu.add(withdrawFromGameAction);
        mi.setMnemonic(KeyEvent.VK_W);

        // Window menu: menu for the "window-related"
        // (satellite windows and graphic actions effecting whole "windows"),
        // Plus the Preferences window as last entry.

        JMenu windowMenu = new JMenu("Window");
        windowMenu.setMnemonic(KeyEvent.VK_W);
        menuBar.add(windowMenu);

        addCheckBox(windowMenu, Options.showCaretaker, KeyEvent.VK_C);
        addCheckBox(windowMenu, Options.showStatusScreen, KeyEvent.VK_G);
        addCheckBox(windowMenu, Options.showEngagementResults, KeyEvent.VK_E);
        addCheckBox(windowMenu, Options.showAutoInspector, KeyEvent.VK_I);
        addCheckBox(windowMenu, Options.showEventViewer, KeyEvent.VK_E);
        addCheckBox(windowMenu, Options.showLogWindow, KeyEvent.VK_L);

        // full recruit tree
        mi = windowMenu.add(viewFullRecruitTreeAction);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0));
        mi.setMnemonic(KeyEvent.VK_R);

        // web client
        mi = windowMenu.add(viewWebClientAction);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0));
        mi.setMnemonic(KeyEvent.VK_W);

        windowMenu.addSeparator();

        // Then the "do something to a Window" actions;
        // and Preferences Window as last:

        if (GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getScreenDevices().length > 1)
        {
            mi = windowMenu.add(chooseScreenAction);
        }

        mi = windowMenu.add(preferencesAction);
        mi.setMnemonic(KeyEvent.VK_P);

        // Then help menu

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        menuBar.add(helpMenu);

        mi = helpMenu.add(aboutAction);
        mi = helpMenu.add(viewReadmeAction);
        mi.setMnemonic(KeyEvent.VK_V);

        mi = helpMenu.add(viewHelpDocAction);
    }

    /**
     * Find the checkbox for the given (boolean) option name; set it to the
     * new given value (only if different that previous value).
     *
     * @param name The option name to adjust the checkbox for
     * @param enable The should-be state of the checkbox
     */
    void adjustCheckboxIfNeeded(String name, boolean enable)
    {
        JCheckBoxMenuItem cbmi = checkboxes.get(name);
        if (cbmi != null)
        {
            // Only set the selected state if it has changed,
            // to avoid infinite feedback loops.
            boolean previous = cbmi.isSelected();
            if (enable != previous)
            {
                cbmi.setSelected(enable);
            }
        }
    }

    /** Show which player owns this board. */
    void setupPlayerLabel()
    {
        if (playerLabelDone)
        {
            return;
        }
        String playerName = gui.getOwningPlayerName();
        cachedPlayerName = playerName;
        if (bottomBar == null)
        {
            // add a bottom bar
            bottomBar = new BottomBar();
            contentPane.add(bottomBar, BorderLayout.SOUTH);

            // notify
            masterFrame.pack();
        }
        bottomBar.setPlayerName(playerName);

        PlayerColor clientColor = client.getColor();
        // If we call this before player colors are chosen, just use
        // the defaults.
        if (clientColor != null)
        {
            Color color = clientColor.getBackgroundColor();
            bottomBar.setPlayerColor(color);
            // Don't do this again.
            playerLabelDone = true;
        }
    }

    private void setupGUIHexes()
    {
        guiHexArray = new GUIMasterHex[getMasterBoard().getHorizSize()][getMasterBoard()
            .getVertSize()];

        int scale = Scale.get();
        int cx = 3 * scale;
        int cy = 0 * scale;

        for (int i = 0; i < guiHexArray.length; i++)
        {
            for (int j = 0; j < guiHexArray[0].length; j++)
            {
                if (getMasterBoard().getShow()[i][j])
                {
                    GUIMasterHex hex = new GUIMasterHex(getMasterBoard()
                        .getPlainHexArray()[i][j]);
                    hex.init(cx + 4 * i * scale, (int)Math.round(cy
                        + (3 * j
                            + ((i + getMasterBoard().getBoardParity()) & 1)
                            * (1 + 2 * (j / 2)) + ((i + 1 + getMasterBoard()
                            .getBoardParity()) & 1)
                            * 2 * ((j + 1) / 2)) * GUIHex.SQRT3 * scale),
                        scale, getMasterBoard().isHexInverted(i, j), this);
                    guiHexArray[i][j] = hex;
                }
            }
        }
    }

    /**
     * TODO this should probably be stored as member, possibly instead of the client class.
     */
    private net.sf.colossus.variant.MasterBoard getMasterBoard()
    {
        return gui.getGame().getVariant().getMasterBoard();
    }

    private void cleanGUIHexes()
    {
        for (int i = 0; i < guiHexArray.length; i++)
        {
            for (int j = 0; j < guiHexArray[0].length; j++)
            {
                if (getMasterBoard().getShow()[i][j])
                {
                    GUIMasterHex hex = guiHexArray[i][j];
                    hex.cleanup();
                    guiHexArray[i][j] = null;
                }
            }
        }
    }

    private void setupPhasePreparations(String titleText)
    {
        unselectAllHexes();
        setTitleInfoText(titleText);
    }

    /**
     * Do the setup needed for an inactive player:
     * set the actions which are allowed only for active player to inactive,
     * and update the bottomBar info why "Done" is disabled accordingly
     *
     * @param text What the active player is doing right now
     */
    private void setupAsInactivePlayer(String text)
    {
        undoLastAction.setEnabled(false);
        undoAllAction.setEnabled(false);
        forcedDoneWithPhaseAction.setEnabled(false);
        takeMulliganAction.setEnabled(false);
        disableDoneActionActivePlayerDoes(text);
    }

    void setupSplitMenu()
    {
        setupPhasePreparations("Split stacks");

        if (gui.isMyTurn())
        {
            undoLastAction.setEnabled(true);
            undoAllAction.setEnabled(true);
            forcedDoneWithPhaseAction.setEnabled(true);
            takeMulliganAction.setEnabled(false);
            enableDoneAction();

            bottomBar.setPhase("Split stacks");
            highlightTallLegions();
            requestFocus();
        }
        else
        {
            setupAsInactivePlayer("splits");
        }
    }

    void setupMoveMenu()
    {
        setupPhasePreparations("Movement Roll: "
            + gui.getGame().getMovementRoll());

        if (gui.isMyTurn())
        {
            undoLastAction.setEnabled(true);
            undoAllAction.setEnabled(true);
            forcedDoneWithPhaseAction.setEnabled(true);
            boolean mullLeft = (gui.getOwningPlayer().getMulligansLeft() > 0);
            takeMulliganAction.setEnabled(mullLeft ? true : false);
            disableDoneAction("At least one legion must move");

            bottomBar.setPhase("Movement");
            highlightUnmovedLegions();
            reqFocus();
        }
        else
        {
            setupAsInactivePlayer("moves");
        }
    }

    void setupFightMenu()
    {
        setupPhasePreparations("Resolve Engagements");

        if (gui.isMyTurn())
        {
            undoLastAction.setEnabled(false);
            undoAllAction.setEnabled(false);
            forcedDoneWithPhaseAction.setEnabled(true);
            takeMulliganAction.setEnabled(false);
            // if there are no engagements, we are kicked to next phase
            // automatically anyway.
            disableDoneAction("still engagements to resolve");

            bottomBar.setPhase("Resolve Engagements");
            highlightEngagements();
            reqFocus();
        }
        else
        {
            setupAsInactivePlayer("fights");
        }
    }

    void setupMusterMenu()
    {
        setupPhasePreparations("Muster Recruits");

        if (gui.isMyTurn())
        {
            // TODO actually it's not a good idea that the ClearRecruitChits
            // action is also allowed in Muster phase - the chit will be
            // cleared from display, but not unrecruited. Might lead to
            // confusion. But then, if one uses that action then it's
            // his own fault ;-)
            undoLastAction.setEnabled(true);
            undoAllAction.setEnabled(true);
            forcedDoneWithPhaseAction.setEnabled(true);
            takeMulliganAction.setEnabled(false);
            enableDoneAction();

            bottomBar.setPhase("Muster Recruits");
            highlightPossibleRecruitLegionHexes();
            reqFocus();
        }
        else
        {
            setupAsInactivePlayer("musters");
        }
    }

    /**
     * Highlight all hexes with legions that (still) can do recruiting
     */
    void highlightPossibleRecruitLegionHexes()
    {
        unselectAllHexes();
        selectHexes(client.getPossibleRecruitHexes());
    }

    KFrame getFrame()
    {
        return masterFrame;
    }

    /** This is incredibly inefficient. */
    void alignAllLegions()
    {
        ArrayHelper.findFirstMatch(getMasterBoard().getPlainHexArray(),
            new NullCheckPredicate<MasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(MasterHex hex)
                {
                    alignLegions(hex);
                    return false;
                }
            });
    }

    void alignLegions(MasterHex masterHex)
    {
        GUIMasterHex hex = getGUIHexByMasterHex(masterHex);
        if (hex == null)
        {
            return;
        }
        List<Legion> legions = gui.getGameClientSide()
            .getLegionsByHex(masterHex);

        int numLegions = legions.size();
        if (numLegions == 0)
        {
            hex.repaint();
            return;
        }

        LegionClientSide legion = (LegionClientSide)legions.get(0);
        Marker marker = legionToMarkerMap.get(legion);
        if (marker == null)
        {
            hex.repaint();
            return;
        }

        int chitScale = marker.getBounds().width;
        Point startingPoint = hex.getOffCenter();
        Point point = new Point(startingPoint);

        if (numLegions == 1)
        {
            // Place legion in the center of the hex.
            int chitScale2 = chitScale / 2;
            point.x -= chitScale2;
            point.y -= chitScale2;
            marker.setLocation(point);
        }
        else if (numLegions == 2)
        {
            // Place legions in NW and SE corners.
            int chitScale4 = chitScale / 4;
            point.x -= 3 * chitScale4;
            point.y -= 3 * chitScale4;
            marker.setLocation(point);

            point = new Point(startingPoint);
            point.x -= chitScale4;
            point.y -= chitScale4;
            legion = (LegionClientSide)legions.get(1);
            marker = legionToMarkerMap.get(legion);
            if (marker != null)
            {
                // Second marker can be null when loading during
                // the engagement phase.
                marker.setLocation(point);
            }
        }
        else if (numLegions == 3)
        {
            // Place legions in NW, SE, NE corners.
            int chitScale4 = chitScale / 4;
            point.x -= 3 * chitScale4;
            point.y -= 3 * chitScale4;
            marker.setLocation(point);

            point = new Point(startingPoint);
            point.x -= chitScale4;
            point.y -= chitScale4;
            legion = (LegionClientSide)legions.get(1);
            marker = legionToMarkerMap.get(legion);
            marker.setLocation(point);

            point = new Point(startingPoint);
            point.x -= chitScale4;
            point.y -= chitScale;
            legion = (LegionClientSide)legions.get(2);
            marker = legionToMarkerMap.get(legion);
            marker.setLocation(point);
        }

        hex.repaint();
    }

    private void alignLegions(Set<MasterHex> hexes)
    {
        for (MasterHex masterHex : hexes)
        {
            alignLegions(masterHex);
        }
    }

    void highlightTallLegions()
    {
        unselectAllHexes();
        selectHexes(client.findTallLegionHexes());
    }

    void highlightUnmovedLegions()
    {
        unselectAllHexes();
        selectHexes(client.findUnmovedLegionHexes());
        repaint();
    }

    /** Select hexes where this legion can move. */
    private void highlightMoves(LegionClientSide legion)
    {
        unselectAllHexes();

        Set<MasterHex> teleport = client.listTeleportMoves(legion);
        selectHexes(teleport, HTMLColor.purple);

        Set<MasterHex> normal = client.listNormalMoves(legion);
        selectHexes(normal, Color.white);

        Set<MasterHex> combo = new HashSet<MasterHex>();
        combo.addAll(teleport);
        combo.addAll(normal);

        gui.addPossibleRecruitChits(legion, combo);
    }

    void highlightEngagements()
    {
        Set<MasterHex> set = gui.getGameClientSide().findEngagements();
        unselectAllHexes();
        selectHexes(set);
    }

    private void setupIcon()
    {
        List<String> directories = new ArrayList<String>();
        directories.add(Constants.defaultDirName
            + StaticResourceLoader.getPathSeparator() + Constants.imagesDirName);

        String[] iconNames = {
            Constants.masterboardIconImage,
            Constants.masterboardIconText + "-Name-"
                + Constants.masterboardIconTextColor,
            Constants.masterboardIconSubscript + "-Subscript-"
                + Constants.masterboardIconTextColor };

        Image image = StaticResourceLoader.getCompositeImage(iconNames, directories,
            60, 60);

        if (image == null)
        {
            LOGGER.log(Level.WARNING, "Couldn't find Colossus icon");
        }
        else
        {
            masterFrame.setIconImage(image);
        }
    }

    /** Do a brute-force search through the hex array, looking for
     *  a match.  Return the hex, or null if none is found. */
    GUIMasterHex getGUIHexByMasterHex(final MasterHex masterHex)
    {
        return ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)
                {
                    return hex.getHexModel().equals(masterHex);
                }
            });
    }

    /** Return the MasterHex that contains the given point, or
     *  null if none does. */
    private GUIMasterHex getHexContainingPoint(final Point point)
    {
        return ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)
                {
                    return hex.contains(point);
                }
            });
    }

    void setMarkerForLegion(Legion legion, Marker marker)
    {
        synchronized (legionToMarkerMap)
        {
            legionToMarkerMap.remove(legion);
            legionToMarkerMap.put(legion, marker);
        }
    }

    void removeMarkerForLegion(Legion legion)
    {
        synchronized (legionToMarkerMap)
        {
            legionToMarkerMap.remove(legion);
            recruitedChits.remove(legion);
        }
    }

    /** Create new markers in response to a rescale. */
    public void recreateMarkers()
    {
        Set<MasterHex> hexesNeedAligning = new HashSet<MasterHex>();
        synchronized (legionToMarkerMap)
        {
            legionToMarkerMap.clear();
            for (Player player : gui.getGameClientSide().getPlayers())
            {
                for (Legion legion : player.getLegions())
                {
                    String markerId = legion.getMarkerId();
                    Marker marker = new Marker(3 * Scale.get(), markerId,
                        client);
                    legionToMarkerMap.put(legion, marker);
                    hexesNeedAligning.add(legion.getCurrentHex());
                }
            }
        }
        alignLegions(hexesNeedAligning);
    }

    /** Return the topmost Marker that contains the given point, or
     *  null if none does. */
    private Marker getMarkerAtPoint(Point point)
    {
        synchronized (legionToMarkerMap)
        {
            Marker marker = null;
            for (Entry<Legion, Marker> entry : legionToMarkerMap.entrySet())
            {
                if (entry.getValue().getBounds().contains(point))
                {
                    marker = entry.getValue();
                }
            }
            return marker;
        }
    }

    // TODO the next couple of methods iterate through all elements of an array
    // by just returning false as predicate value. It should be a different method
    // without return value IMO -- it didn't look as bad when it was called visitor,
    // but a true Visitor shouldn't have the return value. Now the error is the other
    // way around, but at least generified :-)
    void unselectAllHexes()
    {
        ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)

                {
                    if (hex.isSelected())
                    {
                        hex.unselect();
                        hex.repaint();
                    }
                    return false; // keep going
                }
            });
    }

    void selectHex(final MasterHex modelHex)
    {
        ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)
                {
                    if (!hex.isSelected()
                        && modelHex.equals(hex.getHexModel()))
                    {
                        hex.select();
                        hex.repaint();
                    }
                    return false; // keep going
                }
            });
    }

    private void selectHexes(final Set<MasterHex> hexes)
    {
        ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)
                {
                    if (!hex.isSelected() && hexes.contains(hex.getHexModel()))
                    {
                        hex.select();
                        hex.repaint();
                    }
                    return false; // keep going
                }
            });
    }

    private void selectHexes(final Set<MasterHex> hexes, final Color color)
    {
        ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)

                {
                    if (hexes.contains(hex.getHexModel()))
                    {
                        hex.select();
                        hex.setSelectColor(color);
                        hex.repaint();
                    }
                    return false; // keep going
                }
            });
    }

    void actOnMisclick()
    {
        Phase phase = gui.getGame().getPhase();
        if (phase == Phase.SPLIT)
        {
            highlightTallLegions();
        }
        else if (phase == Phase.MOVE)
        {
            clearPossibleRecruitChits();
            gui.setMover(null);
            highlightUnmovedLegions();
        }
        else if (phase == Phase.FIGHT)
        {
            highlightEngagements();
        }
        else if (phase == Phase.MUSTER)
        {
            highlightPossibleRecruitLegionHexes();
        }
    }

    /** Return true if the MouseEvent e came from button 2 or 3.
     *  In theory, isPopupTrigger() is the right way to check for
     *  this.  In practice, the poor design choice of only having
     *  isPopupTrigger() fire on mouse release on Windows makes
     *  it useless here. */
    private static boolean isPopupButton(MouseEvent e)
    {
        int modifiers = e.getModifiers();
        return (((modifiers & InputEvent.BUTTON2_MASK) != 0)
            || ((modifiers & InputEvent.BUTTON3_MASK) != 0) || e.isAltDown() || e
            .isControlDown());
    }

    class MasterBoardMouseHandler extends MouseAdapter
    {
        @Override
        public void mousePressed(MouseEvent e)
        {
            Point point = e.getPoint();
            Marker marker = getMarkerAtPoint(point);
            GUIMasterHex hex = getHexContainingPoint(point);
            if (marker != null)
            {
                String markerId = marker.getId();

                // Move the clicked-on marker to the top of the z-order.
                LegionClientSide legion = client.getLegion(markerId);
                gui.setMarker(legion, marker);

                // Right-click means to show the contents of the legion.
                if (isPopupButton(e))
                {
                    int viewMode = gui.getViewMode();
                    boolean dubiousAsBlanks = gui.getOptions().getOption(
                        Options.dubiousAsBlanks);
                    new ShowLegion(masterFrame, legion, point, scrollPane,
                        4 * Scale.get(), viewMode, client.isMyLegion(legion),
                        dubiousAsBlanks);
                    return;
                }
                else if (client.isMyLegion(legion))
                {
                    if (hex != null)
                    {
                        actOnLegion(legion, hex.getHexModel());
                    }
                    else
                    {
                        LOGGER.log(Level.WARNING,
                            "null hex in MasterBoard.mousePressed()");
                    }
                    return;
                }
            }

            // No hits on chits, so check map.
            if (hex != null)
            {
                if (isPopupButton(e))
                {
                    lastPoint = point;
                    popupMenu.setLabel(hex.getHexModel().getDescription());
                    popupMenu.show(e.getComponent(), point.x, point.y);
                    return;
                }

                // Otherwise, the action to take depends on the phase.
                // Only the current player can manipulate game state.
                if (gui.isMyTurn())
                {
                    actOnHex(hex.getHexModel());
                    hex.repaint();
                    return;
                }
            }

            // No hits on chits or map, so re-highlight.
            if (gui.isMyTurn())
            {
                actOnMisclick();
            }
        }
    }

    class MasterBoardMouseMotionHandler extends MouseMotionAdapter
    {
        @Override
        public void mouseMoved(MouseEvent e)
        {
            Point point = e.getPoint();
            Marker marker = getMarkerAtPoint(point);
            if (marker != null)
            {
                gui.showMarker(marker);
            }
            else
            {
                GUIMasterHex hex = getHexContainingPoint(point);
                if (hex != null)
                {
                    gui.showHexRecruitTree(hex);
                }
            }
        }
    }

    private void actOnLegion(LegionClientSide legion, MasterHex hex)
    {
        if (!gui.isMyTurn())
        {
            return;
        }

        Phase phase = gui.getGame().getPhase();
        if (phase == Phase.SPLIT)
        {
            client.doSplit(legion);
        }
        else if (phase == Phase.MOVE)
        {
            // Allow spin cycle by clicking on chit again.
            if (legion.equals(gui.getMover()))
            {
                actOnHex(hex);
            }
            else
            {
                gui.setMover(legion);
                getGUIHexByMasterHex(hex).repaint();
                highlightMoves(legion);
            }
        }
        else if (phase == Phase.FIGHT)
        {
            client.doFight(hex);
        }
        else if (phase == Phase.MUSTER)
        {
            client.doRecruit(legion);
        }
    }

    private void actOnHex(MasterHex hex)
    {
        Phase phase = gui.getGame().getPhase();
        if (phase == Phase.SPLIT)
        {
            highlightTallLegions();
        }
        else if (phase == Phase.MOVE)
        {
            // If we're moving, and have selected a legion which
            // has not yet moved, and this hex is a legal
            // destination, move the legion here.
            clearRecruitedChits();
            clearPossibleRecruitChits();
            gui.doMove(hex);
            // Would a simple highlightUnmovedLegions() be good enough?
            // Right now its needed also to set mover null...
            // Would a simple highlightUnmovedLegions() be good enough?
            // Right now its needed also to set mover null
            actOnMisclick(); // Yes, even if the move was good.
        }
        else if (phase == Phase.FIGHT)
        {
            // If we're fighting and there is an engagement here, resolve it.
            client.engage(hex);
        }
    }

    class MasterBoardItemHandler implements ItemListener
    {
        public MasterBoardItemHandler()
        {
            super();
            net.sf.colossus.util.InstanceTracker.register(this,
                cachedPlayerName);
        }

        public void itemStateChanged(ItemEvent e)
        {
            JMenuItem source = (JMenuItem)e.getSource();
            String text = source.getText();
            boolean selected = (e.getStateChange() == ItemEvent.SELECTED);
            gui.getOptions().setOption(text, selected);
        }
    }

    class MasterBoardWindowHandler extends WindowAdapter
    {
        @Override
        public void windowClosing(WindowEvent e)
        {
            gui.askNewCloseQuitCancel(masterFrame, false);
        }
    }

    void repaintAfterOverlayChanged()
    {
        overlayChanged = true;
        this.getFrame().repaint();
    }

    @Override
    public void paintComponent(Graphics g)
    {
        // Abort if called too early.
        if (g.getClipBounds() == null)
        {
            return;
        }

        if (offScreenBuffer == null
            || overlayChanged
            || (!(offScreenBuffer.getWidth(this) == this.getSize().width && offScreenBuffer
                .getHeight(this) == this.getSize().height)))
        {
            overlayChanged = false;
            offScreenBuffer = this.createImage(this.getWidth(), this
                .getHeight());
            Graphics g_im = offScreenBuffer.getGraphics();
            super.paintComponent(g_im);

            try
            {
                paintHexes(g_im);
            }
            catch (ConcurrentModificationException ex)
            {
                LOGGER.log(Level.FINEST, "harmless " + ex.toString());
                // Don't worry about it -- we'll just paint again.
            }
        }

        g.drawImage(offScreenBuffer, 0, 0, this);
        try
        {
            paintHighlights((Graphics2D)g);
            paintMarkers(g);
            paintRecruitedChits(g);
            paintPossibleRecruitChits(g);
            paintMovementDie(g);
        }
        catch (ConcurrentModificationException ex)
        {
            LOGGER.log(Level.FINEST, "harmless " + ex.toString());
            // Don't worry about it -- we'll just paint again.
        }
    }

    private void paintHexes(final Graphics g)
    {
        ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)

                {
                    hex.paint(g);
                    return false; // keep going
                }
            });
    }

    private void paintHighlights(final Graphics2D g)
    {
        ArrayHelper.findFirstMatch(guiHexArray,
            new NullCheckPredicate<GUIMasterHex>(false)
            {
                @Override
                public boolean matchesNonNullValue(GUIMasterHex hex)

                {
                    hex.paintHighlightIfNeeded(g);
                    return false; // keep going
                }
            });
    }

    /** Paint markers in z-order. */
    private void paintMarkers(Graphics g)
    {
        synchronized (legionToMarkerMap)
        {
            for (Marker marker : legionToMarkerMap.values())
            {
                if (g.getClipBounds().intersects(marker.getBounds()))
                {
                    marker.paintComponent(g);
                }
            }
        }
    }

    private void paintRecruitedChits(Graphics g)
    {
        for (Chit chit : recruitedChits.values())
        {
            chit.paintComponent(g);
        }
    }

    // all hexes
    public void addPossibleRecruitChits(LegionClientSide legion,
        Set<MasterHex> hexes)
    {
        clearPossibleRecruitChits();

        // set is a set of possible target hexes
        List<CreatureType> oneElemList = new ArrayList<CreatureType>();

        for (MasterHex hex : hexes)
        {
            List<CreatureType> recruits = client.findEligibleRecruits(legion,
                hex);

            if (recruits != null && recruits.size() > 0)
            {
                switch (gui.getRecruitChitMode())
                {
                    case Options.showRecruitChitsNumAll:
                        break;

                    case Options.showRecruitChitsNumRecruitHint:
                        oneElemList.clear();
                        CreatureType hint = client.chooseBestPotentialRecruit(
                            legion, hex, recruits);
                        oneElemList.add(hint);
                        recruits = oneElemList;
                        break;

                    case Options.showRecruitChitsNumStrongest:
                        oneElemList.clear();
                        CreatureType strongest = recruits
                            .get(recruits.size() - 1);
                        oneElemList.add(strongest);
                        recruits = oneElemList;
                        break;
                }
                addPossibleRecruitChits(recruits, hex);
            }
        }
    }

    void addRecruitedChit(Legion legion)
    {
        if (legion.getRecruit() != null)
        {
            MasterHex masterHex = legion.getCurrentHex();
            int scale = 2 * Scale.get();
            GUIMasterHex hex = getGUIHexByMasterHex(masterHex);
            Chit chit = new Chit(scale, legion.getRecruit());
            recruitedChits.put(legion, chit);
            Point startingPoint = hex.getOffCenter();
            Point point = new Point(startingPoint);
            point.x -= scale / 2;
            point.y -= scale / 2;
            chit.setLocation(point);
        }
        repaint();
    }

    void cleanRecruitedChit(LegionClientSide legion)
    {
        recruitedChits.remove(legion);
        repaint();
    }

    // all possible recruit chits, one hex
    private void addPossibleRecruitChits(List<CreatureType> imageNameList,
        MasterHex masterHex)
    {
        List<Chit> list = new ArrayList<Chit>();
        int size = imageNameList.size();
        int num = size;
        for (CreatureType creatureType : imageNameList)
        {
            String imageName = creatureType.getName();
            int scale = 2 * Scale.get();
            GUIMasterHex hex = getGUIHexByMasterHex(masterHex);
            Chit chit = new Chit(scale, imageName);
            Point startingPoint = hex.getOffCenter();
            Point point = new Point(startingPoint);
            point.x -= scale / 2;
            point.y -= scale / 2;
            int offset = (num - ((size / 2) + 1));
            point.x += ((offset * scale) + ((size % 2 == 0 ? (scale / 2) : 0)))
                / size;
            point.y += ((offset * scale) + ((size % 2 == 0 ? (scale / 2) : 0)))
                / size;
            num--;
            chit.setLocation(point);
            list.add(chit);
        }
        possibleRecruitChits.put(masterHex, list);
    }

    public void clearRecruitedChits()
    {
        for (Chit chit : recruitedChits.values())
        {
            remove(chit);
        }
        recruitedChits.clear();
    }

    public void clearPossibleRecruitChits()
    {
        Set<MasterHex> hexes = possibleRecruitChits.keySet();
        possibleRecruitChits.clear();
        for (MasterHex hex : hexes)
        {
            GUIMasterHex guiHex = getGUIHexByMasterHex(hex);
            guiHex.repaint();
        }
    }

    private void paintPossibleRecruitChits(Graphics g)
    {
        // Each returned list is the list of chits for one hex
        for (List<Chit> chits : possibleRecruitChits.values())
        {
            for (Chit chit : chits)
            {
                chit.paintComponent(g);
            }
        }
    }

    private void paintMovementDie(Graphics g)
    {
        if (client != null)
        {
            MovementDie die = gui.getMovementDie();
            if (die != null)
            {
                die.setLocation(0, 0);
                die.paintComponent(g);
            }
            else
            {
                // Paint a black square in the upper-left corner.
                g.setColor(Color.black);
                g.fillRect(0, 0, 4 * Scale.get(), 4 * Scale.get());
            }
        }
    }

    @Override
    public Dimension getMinimumSize()
    {
        int scale = Scale.get();
        return new Dimension(((getMasterBoard().getHorizSize() + 1) * 4)
            * scale, (getMasterBoard().getVertSize() * 7) * scale);
    }

    @Override
    public Dimension getPreferredSize()
    {
        return getMinimumSize();
    }

    void rescale()
    {
        setupGUIHexes();
        recreateMarkers();
        setSize(getPreferredSize());
        masterFrame.pack();
        repaint();
    }

    void deiconify()
    {
        if (masterFrame.getState() == JFrame.ICONIFIED)
        {
            masterFrame.setState(JFrame.NORMAL);
        }
    }

    public void dispose()
    {
        setVisible(false);
        setEnabled(false);
        saveWindow.saveLocation(masterFrame.getLocation());
        saveWindow = null;
        cleanCBListeners();
        masterFrame.setVisible(false);
        masterFrame.setEnabled(false);
        masterFrame.removeWindowListener(mbwh);
        masterFrame.dispose();
        masterFrame = null;
        scrollPane = null;

        removeKeyListener(this.iph);
        if (showReadme != null)
        {
            showReadme.dispose();
            showReadme = null;
        }
        if (showHelpDoc != null)
        {
            showHelpDoc.dispose();
            showHelpDoc = null;
        }
        offScreenBuffer = null;
        iph = null;
        cleanGUIHexes();

        // not those, they are static (common for all objects)
        // first client disposing would set it null, others get NPE's ...
        //plainHexArray = null;
        //towerSet = null;

        this.client = null;
    }

    void pack()
    {
        masterFrame.pack();
    }

    void updateComponentTreeUI()
    {
        SwingUtilities.updateComponentTreeUI(this);
        SwingUtilities.updateComponentTreeUI(masterFrame);
    }

    void fullRepaint()
    {
        masterFrame.repaint();
        repaint();
    }

    void reqFocus()
    {
        if (gui.getOptions().getOption(Options.stealFocus))
        {
            requestFocus();
            getFrame().toFront();
        }
    }

    class BottomBar extends JPanel
    {
        private final JLabel playerLabel;

        /** quick access button to the doneWithPhase action.
         *  must be en- and disabled often.
         */
        private final JButton doneButton;

        private final JButton suspendButton;

        /** display the current phase in the bottom bar */
        private final JLabel phaseLabel;

        /**
         * Displays reasons why "Done" can not be used.
         */
        private final JLabel todoLabel;

        public void setPlayerName(String s)
        {
            playerLabel.setText(s);
        }

        public void setPlayerColor(Color color)
        {
            playerLabel.setForeground(color);
        }

        public void setPhase(String s)
        {
            phaseLabel.setText(s);
        }

        public void setReasonForDisabledDone(String reason)
        {
            todoLabel.setText("(" + reason + ")");
        }

        public BottomBar()
        {
            super();

            setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

            String text = gui.getClient().isSuspended() ? "Resume" : "Suspend";
            suspendButton = new JButton(text);
            suspendButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    MasterBoard.this.bottomBar.toggleSuspend();
                }
            });

            add(suspendButton);

            playerLabel = new JLabel("- player -");
            add(playerLabel);

            doneButton = new JButton(doneWithPhaseAction);
            doneWithPhaseAction
                .addPropertyChangeListener(new PropertyChangeListener()
                {
                    public void propertyChange(PropertyChangeEvent evt)
                    {
                        if (evt.getPropertyName().equals("enabled")
                            && evt.getNewValue().equals(Boolean.TRUE))
                        {
                            todoLabel.setText("");
                        }
                    }
                });
            add(doneButton);

            phaseLabel = new JLabel("- phase -");
            add(phaseLabel);

            todoLabel = new JLabel();
            add(todoLabel);
        }

        public void toggleSuspend()
        {
            boolean oldState = gui.getClient().isSuspended();
            boolean newState = !oldState;
            gui.getClient().setGuiSuspendOngoing(newState);
            String text = gui.getClient().isSuspended() ? "Resume" : "Suspend";
            suspendButton.setText(text);
        }
    }

    public void enableDoneAction()
    {
        doneWithPhaseAction.setEnabled(true);
    }

    /**
     * Disable the Done action, and update the reason text in bottomBar
     *
     * @param reason Information why one is not ready to be Done
     */
    public void disableDoneAction(String reason)
    {
        doneWithPhaseAction.setEnabled(false);
        bottomBar.setReasonForDisabledDone(reason);
    }

    /**
     * Clear bottomBar phase text and call disableDoneAction, as reason the
     * standard text "&lt;active player> doesWhat"
     *
     * @param doesWhat Information what the active player currently does
     */
    private void disableDoneActionActivePlayerDoes(String doesWhat)
    {
        bottomBar.setPhase("");
        String name = gui.getClient().getActivePlayer().getName();
        disableDoneAction(name + " " + doesWhat);
    }

    private void makeDoneCloseWindow()
    {
        gameOverStateReached = true;
        enableDoneAction();
    }

    public void setServerClosedMessage(boolean gameOver)
    {
        if (gameOver)
        {
            bottomBar.setPhase("Game over");
            disableDoneAction("game server closed connection");
        }
        else
        {
            bottomBar.setPhase("Unable to continue game");
            disableDoneAction("connection to server lost");
        }
        makeDoneCloseWindow();
    }

    public void setReplayMode()
    {
        disableDoneAction("please wait...");
    }

    public void updateReplayText(int currTurn, int maxTurn)
    {
        bottomBar.setPhase("Replay ongoing, " + currTurn + " of " + maxTurn
            + " turns processed");
    }

    private void setTitleInfoText(String text)
    {
        masterFrame.setTitle(client.getActivePlayer() + " Turn "
            + client.getTurnNumber() + " : " + text);
    }

    public void setGameOverState(String message)
    {
        setTitleInfoText("Game Over -- " + message);
        bottomBar.setPhase("Game Over: " + message);
        disableDoneAction("connection closed from server side");
        makeDoneCloseWindow();
    }

    public void setPhaseInfo(String message)
    {
        bottomBar.setPhase(message);
    }
}
