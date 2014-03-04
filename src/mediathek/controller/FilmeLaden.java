/*
 * MediathekView
 * Copyright (C) 2008 W. Xaver
 * W.Xaver[at]googlemail.com
 * http://zdfmediathk.sourceforge.net/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package mediathek.controller;

import java.util.HashSet;
import java.util.Iterator;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import mediathek.daten.Daten;
import mediathek.gui.dialog.DialogLeer;
import mediathek.gui.dialogEinstellungen.PanelFilmlisteLaden;
import mediathek.tool.Duration;
import mediathek.tool.GuiFunktionen;
import mediathek.tool.GuiKonstanten;
import mediathek.tool.MVListeFilme;
import mediathek.tool.MVMessageDialog;
import msearch.daten.DatenFilm;
import msearch.daten.ListeFilme;
import msearch.daten.MSConfig;
import msearch.filmeLaden.ListeDownloadUrlsFilmlisten;
import msearch.filmeLaden.ListeFilmlistenServer;
import msearch.filmeLaden.MSImportFilmliste;
import msearch.filmeSuchen.MSFilmeSuchen;
import msearch.filmeSuchen.MSListenerFilmeLaden;
import msearch.filmeSuchen.MSListenerFilmeLadenEvent;

public class FilmeLaden {

    // public static
    public static final int UPDATE_FILME_AUS = 0; // nix
    public static final int UPDATE_FILME_URL = 1; // manuell laden, Url automatisch wählen
    public static final int UPDATE_FILME_AUTO = 2; // beim Start, immer mal wieder, + Url auto
    public static final int ALTER_FILMLISTE_SEKUNDEN_FUER_AUTOUPDATE = 3 * 60 * 60; // beim Start des Programms wir die Liste geladen wenn sie älter ist als ..
    private Duration duration = new Duration(FilmeLaden.class.getSimpleName());
    private HashSet<String> hashSet = new HashSet<>();
    private ListeFilme diffListe = new ListeFilme();

    private static enum ListenerMelden {

        START, PROGRESS, FINISHED
    };
    // private
    private MSFilmeSuchen mSearchFilmeSuchen;
    private MSImportFilmliste mSearchImportFilmliste;
    private EventListenerList listeners = new EventListenerList();
    private boolean istAmLaufen = false;

    public FilmeLaden() {
        mSearchFilmeSuchen = new MSFilmeSuchen();
        mSearchFilmeSuchen.addAdListener(new MSListenerFilmeLaden() {
            @Override
            public synchronized void start(MSListenerFilmeLadenEvent event) {
                notifyStart(event);
            }

            @Override
            public synchronized void progress(MSListenerFilmeLadenEvent event) {
                notifyProgress(event);
            }

            @Override
            public synchronized void fertig(MSListenerFilmeLadenEvent event) {
                // Ergebnisliste listeFilme eintragen -> Feierabend!
                Daten.listeFilme = mSearchFilmeSuchen.listeFilmeNeu;
                undEnde(event);
            }
        });
        mSearchImportFilmliste = new MSImportFilmliste();
        mSearchImportFilmliste.addAdListener(new MSListenerFilmeLaden() {
            @Override
            public synchronized void start(MSListenerFilmeLadenEvent event) {
                notifyStart(event);
            }

            @Override
            public synchronized void progress(MSListenerFilmeLadenEvent event) {
                notifyProgress(event);
            }

            @Override
            public synchronized void fertig(MSListenerFilmeLadenEvent event) {
                // Ergebnisliste listeFilme eintragen -> Feierabend!
                duration.stop("Filme laden, ende");
                //Daten.filmlisteSpeichern();
                undEnde(event);
            }
        });
    }

    // ###########################
    public synchronized void setStop(boolean set) {
        MSConfig.setStop(set);
    }

    public String[] getSenderNamen() {
        return mSearchFilmeSuchen.getNamenSender();
    }

    // ###################################################################
    // Filme laden
    // ###################################################################
    public void filmeLaden(Daten daten, boolean manuell) {
        if (manuell || GuiFunktionen.getImportArtFilme() == GuiKonstanten.UPDATE_FILME_AUS) {
            // Dialog zum Laden der Filme anzeigen
            DialogLeer dialog = new DialogLeer(daten.mediathekGui, true);
            dialog.init("Einstellungen zum Laden der Filme", new PanelFilmlisteLaden(daten, daten.mediathekGui, dialog));
            dialog.setVisible(true);
        } else {
            // Filme werden automatisch geladen
            Daten.filmeLaden.importFilmliste("");
        }
    }

    // #########################################################
    // Filme als Liste importieren
    // #########################################################
    public void importFilmliste(String dateiUrl) {
        // damit wird die Filmliste geladen UND auch gleich im Konfig-Ordner gespeichert
        duration.start("Filme laden, start");
        if (!istAmLaufen) {
            // nicht doppelt starten
            istAmLaufen = true;
            // Hash mit URLs füllen
            hashSet.clear();
            fillHash(Daten.listeFilme);
            Daten.listeFilme.clear();
            Daten.listeFilmeNachBlackList.clear();
            System.gc();
            if (dateiUrl.equals("")) {
                // Filme als Liste importieren, Url automatisch ermitteln
                Log.systemMeldung("Aktuelle Filmliste laden");
                mSearchImportFilmliste.filmeImportierenAuto(Daten.getDateiFilmliste(), Daten.listeFilme);
            } else {
                // Filme als Liste importieren, feste URL/Datei
                Log.systemMeldung("Filmliste laden von: " + dateiUrl);
                mSearchImportFilmliste.filmeImportierenDatei(dateiUrl, Daten.getDateiFilmliste(), Daten.listeFilme);
            }
        }
    }

    public void updateFilmliste(String dateiUrl) {
        // damit wird die Filmliste mit einer weiteren aktualisiert (die bestehende bleibt
        // erhalten) UND auch gleich im Konfig-Ordner gespeichert
        duration.start("Filme laden (Update), start");
        if (!istAmLaufen) {
            // nicht doppelt starten
            istAmLaufen = true;
            // Hash mit URLs füllen
            hashSet.clear();
            fillHash(Daten.listeFilme);
            //Daten.listeFilme.clear();
            Daten.listeFilmeNachBlackList.clear();
            System.gc();
            // Filme als Liste importieren, feste URL/Datei
            Log.systemMeldung("Filmliste laden von: " + dateiUrl);
            mSearchImportFilmliste.filmeImportierenDatei(dateiUrl, Daten.getDateiFilmliste(), diffListe);
        }
    }

    // #######################################
    // Filme bei den Sendern laden
    // #######################################
    public void filmeBeimSenderSuchen(ListeFilme llisteFilme, boolean senderAllesLaden, boolean filmlisteUpdate) {
        // Filme bei allen Sender suchen
        if (!istAmLaufen) {
            // nicht doppelt starten
            istAmLaufen = true;
            hashSet.clear();
            Daten.listeFilme.neueFilme = false;
            Daten.listeFilmeNachBlackList.neueFilme = false;
            fillHash(Daten.listeFilme);
            MSConfig.senderAllesLaden = senderAllesLaden;
            MSConfig.updateFilmliste = filmlisteUpdate;
            MSConfig.debug = Daten.debug;
            mSearchFilmeSuchen.filmeBeimSenderLaden(Daten.listeFilme);
        }
    }

    public void updateSender(String[] sender, ListeFilme llisteFilme, boolean senderAllesLaden) {
        // Filme nur bei EINEM Sender suchen (nur update)
        if (!istAmLaufen) {
            // nicht doppelt starten
            istAmLaufen = true;
            hashSet.clear();
            Daten.listeFilme.neueFilme = false;
            Daten.listeFilmeNachBlackList.neueFilme = false;
            fillHash(Daten.listeFilme);
            MSConfig.senderAllesLaden = senderAllesLaden;
            MSConfig.debug = Daten.debug;
            mSearchFilmeSuchen.updateSender(sender, Daten.listeFilme);
        }
    }

    public ListeFilmlistenServer getListeFilmlistnServer() {
        return mSearchImportFilmliste.getListe_FilmlistenServer();
    }

    public ListeDownloadUrlsFilmlisten getDownloadUrlsFilmlisten(boolean update) {
        return mSearchImportFilmliste.getDownloadUrls_Filmlisten(update);
    }

    private void undEnde(MSListenerFilmeLadenEvent event) {
        // Abos eintragen in der gesamten Liste vor Blacklist da das nur beim Ändern der Filmliste oder
        // beim Ändern von Abos gemacht wird

        // wenn nur ein Update
        if (!diffListe.isEmpty()) {
            Daten.listeFilme.updateListe(diffListe, false /* nur URL vergleichen */, true /*ersetzen*/);
            Daten.listeFilme.metaDaten = diffListe.metaDaten;
            Daten.listeFilme.sort(); // jetzt sollte alles passen
        }

        searchHash(Daten.listeFilme);
        Daten.listeFilme.themenLaden();
        Daten.listeAbo.setAboFuerFilm(Daten.listeFilme, false/*aboLoeschen*/);
        istAmLaufen = false;
        if (event.fehler) {
            MVMessageDialog.showMessageDialog(null, "Das Laden der Filmliste hat nicht geklappt!", "Fehler", JOptionPane.ERROR_MESSAGE);
        }
        notifyFertig(event);
    }

    private void fillHash(ListeFilme listeFilme) {
        Iterator<DatenFilm> it = listeFilme.iterator();
        while (it.hasNext()) {
            hashSet.add(it.next().arr[DatenFilm.FILM_URL_NR]);
        }
    }

    private void searchHash(ListeFilme listeFilme) {
        Iterator<DatenFilm> it = listeFilme.iterator();
        while (it.hasNext()) {
            DatenFilm datenFilm = it.next();
            if (!hashSet.contains(datenFilm.arr[DatenFilm.FILM_URL_NR])) {
                datenFilm.neuerFilm = true;
                listeFilme.neueFilme = true;
            }
        }
        hashSet.clear();
    }
    // ###########################
    // Listener
    // ###########################

    public void addAdListener(MSListenerFilmeLaden listener) {
        listeners.add(MSListenerFilmeLaden.class, listener);
    }

    private void notifyStart(MSListenerFilmeLadenEvent event) {
        for (MSListenerFilmeLaden l : listeners.getListeners(MSListenerFilmeLaden.class)) {
            run_(new Start(l, event, ListenerMelden.START));
        }
    }

    private void notifyProgress(MSListenerFilmeLadenEvent event) {
        for (MSListenerFilmeLaden l : listeners.getListeners(MSListenerFilmeLaden.class)) {
            run_(new Start(l, event, ListenerMelden.PROGRESS));
        }
    }

    private void notifyFertig(MSListenerFilmeLadenEvent event) {
        for (MSListenerFilmeLaden l : listeners.getListeners(MSListenerFilmeLaden.class)) {
            run_(new Start(l, event, ListenerMelden.FINISHED));
        }
    }

    private class Start implements Runnable {

        private MSListenerFilmeLaden listenerFilmeLaden;
        private MSListenerFilmeLadenEvent event;
        private ListenerMelden listenerMelden;

        public Start(MSListenerFilmeLaden llistenerFilmeLaden, MSListenerFilmeLadenEvent eevent, ListenerMelden lliListenerMelden) {
            listenerFilmeLaden = llistenerFilmeLaden;
            event = eevent;
            listenerMelden = lliListenerMelden;
        }

        @Override
        public synchronized void run() {
            switch (listenerMelden) {
                case START:
                    listenerFilmeLaden.start(event);
                    break;
                case PROGRESS:
                    listenerFilmeLaden.progress(event);
                    break;
                case FINISHED:
                    listenerFilmeLaden.fertig(event);
                    MVListeFilme.checkBlacklist();
                    break;
            }
        }
    }

    private void run_(Runnable r) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                // entweder hier
                r.run();
            } else {
                SwingUtilities.invokeLater(r);
            }
        } catch (Exception ex) {
            Log.fehlerMeldung(926369741, Log.FEHLER_ART_PROG, "ListenerFilmeLaden.run_", ex);
        }
    }
}
