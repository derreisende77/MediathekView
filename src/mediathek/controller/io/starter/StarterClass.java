/*    
 *    MediathekView
 *    Copyright (C) 2008   W. Xaver
 *    W.Xaver[at]googlemail.com
 *    http://zdfmediathk.sourceforge.net/
 *    
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package mediathek.controller.io.starter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import mediathek.daten.DDaten;
import mediathek.daten.DatenDownload;
import mediathek.daten.DatenFilm;
import mediathek.daten.DatenPset;
import mediathek.tool.Konstanten;
import mediathek.tool.ListenerMediathekView;
import mediathek.tool.Log;
import mediathek.tool.TModel;

public class StarterClass {

    private DDaten ddaten;
    private ListeStarts listeStarts;
    private Starten starten = null;

    //===================================
    // Public
    //===================================
    public StarterClass(DDaten d) {
        ddaten = d;
        init();
    }

    private void init() {
        listeStarts = new ListeStarts(ddaten);
        starten = new Starten();
        Thread startenThread = new Thread(starten);
        startenThread.setDaemon(true);
        startenThread.start();
    }

    public synchronized Start urlStarten(DatenPset pSet, DatenFilm ersterFilm) {
        // url mit dem Programm mit der Nr. starten (Button oder Doppelklick)
        // Quelle "Button" ist immer ein vom User gestarteter Film, also Quelle_Button!!!!!!!!!!!
        Start s = null;
        String url = ersterFilm.arr[DatenFilm.FILM_URL_NR];
        if (!url.equals("")) {
            s = new Start(new DatenDownload(pSet, ersterFilm, Start.QUELLE_BUTTON, null, "", ""));
            this.starten.startStarten(s);
            addStarts(s);
        }
        return s;
    }

    public synchronized Start urlVorziehen(String url) {
        // Starts mit der URL wird vorgezogen und startet als nächster
        Start s = null;
        Iterator<Start> it = listeStarts.getIt();
        while (it.hasNext()) {
            s = it.next();
            if (s.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR].equals(url)) {
                if (s.status < Start.STATUS_RUN) {
                    // sonst bringts nichts mehr
                    it.remove();
                    listeStarts.addFirst(s);
                }
                break;
            }
        }
        return s;
    }

    public synchronized LinkedList<Start> getStarts(int quelle) {
        LinkedList<Start> ret = new LinkedList<Start>();
        Iterator<Start> it = listeStarts.getIt();
        while (it.hasNext()) {
            Start s = it.next();
            if (s.datenDownload.getQuelle() == quelle || quelle == Start.QUELLE_ALLE) {
                ret.add(s);
            }
        }
        return ret;
    }

    public synchronized int getDownloadsWarten() {
        int ret = 0;
        Iterator<Start> it = listeStarts.getIt();
        while (it.hasNext()) {
            Start s = it.next();
            if (s.datenDownload.getQuelle() == Start.QUELLE_ABO || s.datenDownload.getQuelle() == Start.QUELLE_DOWNLOAD) {
                if (s.status == Start.STATUS_INIT) {
                    ++ret;
                }
            }
        }
        return ret;
    }

    public synchronized int getDownloadsLaufen() {
        int ret = 0;
        Iterator<Start> it = listeStarts.getIt();
        while (it.hasNext()) {
            Start s = it.next();
            if (s.datenDownload.getQuelle() == Start.QUELLE_ABO || s.datenDownload.getQuelle() == Start.QUELLE_DOWNLOAD) {
                if (s.status == Start.STATUS_RUN) {
                    ++ret;
                }
            }
        }
        return ret;
    }

    public synchronized int getStartsWaiting() {
        // für "Auto", wenn alle abgearbeitet sind, dann fertig
        return listeStarts.getmax();
    }

    public synchronized TModel getStarterModell(TModel model) {
        return listeStarts.getModel(model);

    }

    public synchronized void addStarts(Start start) {
        //add: Neues Element an die Liste anhängen
        if (start != null) {
            if (!listeStarts.contain(start)) {
                listeStarts.add(start);
                // gestartete Filme auch in die History eintragen
                ddaten.history.add(start.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR]);
            }
        }
        notifyStartEvent();
    }

    public synchronized Start getStart(String url) {
        Start ret = null;
        Iterator<Start> it = listeStarts.getIt();
        while (it.hasNext()) {
            Start s = it.next();
            if (s.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR].equals(url)) {
                ret = s;
                break;
            }
        }
        return ret;
    }

    public synchronized void aufraeumen() {
        listeStarts.aufraeumen();
        notifyStartEvent();
    }

    public synchronized void allesAbbrechen() {
        // Alle Downloads werden abgebrochen
        listeStarts.delStart();
        notifyStartEvent();
    }

    public synchronized void filmLoeschen(String url) {
        listeStarts.delStart(url);
        notifyStartEvent();
    }

    //===================================
    // Private
    //===================================
    private void notifyStartEvent() {
        ListenerMediathekView.notify(ListenerMediathekView.EREIGNIS_START_EVENT, StarterClass.class.getSimpleName());
    }

    private void buttonStartsPutzen() {
        // Starts durch Button die fertig sind, löschen
        boolean habsGetan = false;
        Iterator<Start> it = listeStarts.getIt();
        while (it.hasNext()) {
            Start s = it.next();
            if (s.datenDownload.getQuelle() == Start.QUELLE_BUTTON) {
                if (s.status != Start.STATUS_RUN) {
                    // dann ist er fertig oder abgebrochen
                    it.remove();
                    habsGetan = true;
                }
            }
        }
        if (habsGetan) {
            notifyStartEvent(); // und dann bescheid geben
        }
    }

    private Start getListe() {
        // get: erstes passendes Element der Liste zurückgeben oder null
        // und versuchen dass bei mehreren laufenden Downloads ein anderer Sender gesucht wird
        Iterator<Start> it;
        Start ret = null;
        if (listeStarts.size() >= 0
                && listeStarts.getDown() < Integer.parseInt(DDaten.system[Konstanten.SYSTEM_MAX_DOWNLOAD_NR])) {
            Start s = naechsterStart();
            if (s != null) {
                if (s.status == Start.STATUS_INIT) {
                    ret = s;
                }
            }
        }
        return ret;
    }

    private Start naechsterStart() {
        Start s;
        Iterator<Start> it = listeStarts.getIt();
        //erster Versuch, Start mit einem anderen Sender
        while (it.hasNext()) {
            s = it.next();
            if (s.status == Start.STATUS_INIT) {
                if (!maxSenderLaufen(s, 1)) {
                    return s;
                }
            }
        }
        if (Konstanten.MAX_SENDER_FILME_LADEN == 1) {
            //dann wars dass
            return null;
        }
        //zweiter Versuch, Start mit einem passenden Sender
        it = listeStarts.getIt();
        while (it.hasNext()) {
            s = it.next();
            if (s.status == Start.STATUS_INIT) {
                //int max = s.film.arr[Konstanten.FILM_SENDER_NR].equals(Konstanten.SENDER_PODCAST) ? Konstanten.MAX_PODCAST_FILME_LADEN : Konstanten.MAX_SENDER_FILME_LADEN;
                if (!maxSenderLaufen(s, Konstanten.MAX_SENDER_FILME_LADEN)) {
                    return s;
                }
            }
        }
        return null;
    }

    private boolean maxSenderLaufen(Start s, int max) {
        //true wenn bereits die maxAnzahl pro Sender läuft
        try {
            int counter = 0;
            Start start;
            String host = getHost(s);
            Iterator<Start> it = listeStarts.getIt();
            while (it.hasNext()) {
                start = it.next();
                if (start.status == Start.STATUS_RUN
                        && getHost(start).equalsIgnoreCase(host)) {
                    counter++;
                    if (counter >= max) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private String getHost(Start s) {
        String host = "";
        try {
            try {
                String uurl = s.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR];
                // die funktion "getHost()" kann nur das Protokoll "http" ??!??
                if (uurl.startsWith("rtmpt:")) {
                    uurl = uurl.toLowerCase().replace("rtmpt:", "http:");
                }
                if (uurl.startsWith("rtmp:")) {
                    uurl = uurl.toLowerCase().replace("rtmp:", "http:");
                }
                if (uurl.startsWith("mms:")) {
                    uurl = uurl.toLowerCase().replace("mms:", "http:");
                }
                URL url = new URL(uurl);
                String tmp = url.getHost();
                if (tmp.contains(".")) {
                    host = tmp.substring(tmp.lastIndexOf("."));
                    tmp = tmp.substring(0, tmp.lastIndexOf("."));
                    if (tmp.contains(".")) {
                        host = tmp.substring(tmp.lastIndexOf(".") + 1) + host;
                    } else if (tmp.contains("/")) {
                        host = tmp.substring(tmp.lastIndexOf("/") + 1) + host;
                    } else {
                        host = "host";
                    }
                }
            } catch (Exception ex) {
                // für die Hosts bei denen das nicht klappt
                // Log.systemMeldung("getHost 1: " + s.download.arr[DatenDownload.DOWNLOAD_URL_NR]);
                host = "host";
            } finally {
                if (host == null) {
                    // Log.systemMeldung("getHost 2: " + s.download.arr[DatenDownload.DOWNLOAD_URL_NR]);
                    host = "host";
                }
                if (host.equals("")) {
                    // Log.systemMeldung("getHost 3: " + s.download.arr[DatenDownload.DOWNLOAD_URL_NR]);
                    host = "host";
                }
            }
        } catch (Exception ex) {
            // Log.systemMeldung("getHost 4: " + s.download.arr[DatenDownload.DOWNLOAD_URL_NR]);
            host = "exception";
        }
        return host;
    }

    // ********************************************
    // Hier wird dann gestartet
    // Ewige Schleife die die Downloads startet
    // ********************************************
    private class Starten implements Runnable {

        Start start;

        @Override
        public synchronized void run() {
            while (true) {
                try {
                    while ((start = getListe()) != null) {
                        startStarten(start);
                        //alle 5 Sekunden einen Download starten
                        this.wait(5000);
                    }
                    buttonStartsPutzen(); // Button Starts aus der Liste löschen
                    this.wait(3000);
                } catch (Exception ex) {
                    Log.fehlerMeldung(613822015, "StarterClass.Starten.run", ex);
                }
            } //while(true)
        }

        private void startStarten(Start start) {
            start.datenDownload.startMelden(DatenDownload.PROGRESS_GESTARTET);
            switch (start.datenDownload.getArt()) {
                case Start.ART_PROGRAMM:
                    StartenProgramm startenProgrammn = new StartenProgramm(start);
                    new Thread(startenProgrammn).start();
                    break;
                case Start.ART_DOWNLOAD:
                    StartenDonwnload startenDonwnloadtart = new StartenDonwnload(start);
                    new Thread(startenDonwnloadtart).start();
                    break;
                default:
                    Log.fehlerMeldung(789356001, "StartetClass.startStarten", "StarterClass.Starten - Switch-default");
                    break;
            }
        }
    }

    private class StartenProgramm implements Runnable {

        Start start;
        RuntimeExec runtimeExec;
        File file;

        public StartenProgramm(Start s) {
            start = s;
            start.status = Start.STATUS_RUN;
            file = new File(start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
            notifyStartEvent();
            try {
                new File(start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_NR]).mkdirs();
            } catch (Exception ex) {
                Log.fehlerMeldung(469365281, "StarterClass.StartenProgramm-1", ex);
            }
        }

        @Override
        public synchronized void run() {
            long filesize = -1;
            final int stat_start = 0;
            final int stat_laufen = 1;
            final int stat_restart = 3;
            final int stat_pruefen = 4;
            // ab hier ist schluss
            final int stat_fertig_ok = 10;
            final int stat_fertig_fehler = 11;
            final int stat_ende = 99;
            int stat = stat_start;
            try {
                while (stat < stat_ende) {
                    switch (stat) {
                        case stat_start:
                            // versuch das Programm zu Starten
                            if (starten()) {
                                stat = stat_laufen;
                            } else {
                                stat = stat_restart;
                            }
                            break;
                        case stat_laufen:
                            //hier läuft der Download bis zum Abbruch oder Ende
                            try {
                                if (start.stoppen) {
                                    stat = stat_fertig_ok;
                                    if (start.process != null) {
                                        start.process.destroy();
                                    }
                                } else {
                                    int exitV = start.process.exitValue();
                                    if (exitV != 0) {
                                        stat = stat_restart;
                                    } else {
                                        stat = stat_pruefen;
                                    }
                                }
                            } catch (Exception ex) {
                                try {
                                    this.wait(2000);
                                } catch (InterruptedException e) {
                                }
                            }
                            break;
                        case stat_restart:
                            if (!start.datenDownload.isRestart()) {
                                // dann wars das
                                stat = stat_fertig_fehler;
                            } else {
                                if (filesize == -1) {
                                    //noch nichts geladen
                                    leeresFileLoeschen(file);
                                    if (file.exists()) {
                                        // dann bestehende Datei weitermachen
                                        filesize = file.length();
                                        stat = stat_start;
                                    } else {
                                        // counter prüfen und bei einem Maxwert abbrechen, sonst endlos
                                        if (start.startcounter < Start.STARTCOUNTER_MAX) {
                                            // dann nochmal von vorne
                                            stat = stat_start;
                                        } else {
                                            // dann wars das
                                            stat = stat_fertig_fehler;
                                        }
                                    }
                                } else {
                                    //jetzt muss das File wachsen, sonst kein Restart
                                    if (!file.exists()) {
                                        // dann wars das
                                        stat = stat_fertig_fehler;
                                    } else {
                                        if (file.length() > filesize) {
                                            //nur weitermachen wenn die Datei tasächlich wächst
                                            filesize = file.length();
                                            stat = stat_start;
                                        } else {
                                            // dann wars das
                                            stat = stat_fertig_fehler;
                                        }
                                    }
                                }
                            }
                            break;
                        case stat_pruefen:
                            if (start.datenDownload.getQuelle() == Start.QUELLE_BUTTON) {
                                //für die direkten Starts mit dem Button wars das dann
                                stat = stat_fertig_ok;
                            } else if (pruefen(start)) {
                                //fertig und OK
                                stat = stat_fertig_ok;
                            } else {
                                //fertig und fehlerhaft
                                stat = stat_fertig_fehler;
                            }
                            break;
                        case stat_fertig_fehler:
                            start.status = Start.STATUS_ERR;
                            stat = stat_ende;
                            break;
                        case stat_fertig_ok:
                            start.status = Start.STATUS_FERTIG;
                            stat = stat_ende;
                            break;
                    }
                }
            } catch (Exception ex) {
                Log.fehlerMeldung(395623710, "StarterClass.StartenProgramm-2", ex);
            }
            leeresFileLoeschen(file);
            fertigmeldung(start);
            start.datenDownload.startMelden(DatenDownload.PROGRESS_FERTIG);
            notifyStartEvent();
        }

        private boolean starten() {
            boolean ret = false;
            // die Reihenfolge: startcounter - startmeldung ist wichtig!
            start.startcounter++;
            startmeldung(start);
            runtimeExec = new RuntimeExec(start);
            start.process = runtimeExec.exec();
            if (start.process != null) {
                ret = true;
            }
            return ret;
        }
    }
//        public synchronized void run_old() {
//            int k = 0;
//            long filesize = -1;
//            boolean restart = false;
//            boolean startOk = false;
//            try {
//                if (starten("")) {
//                    restart = true; //los gehts
//                }
//                while (restart && !starts.stoppen) {
//                    startOk = false;
//                    restart = false;
//                    while (!allesStop && !starts.stoppen) {
//                        //hier läuft der Download bis zum Abbruch oder Ende
//                        try {
//                            k = starts.process.exitValue();
//                            //fertig und tschüss
//                            break;
//                        } catch (Exception ex) {
//                            try {
//                                this.wait(2000);
//                            } catch (InterruptedException e) {
//                            }
//                        }
//                    }
//                    if (allesStop || starts.stoppen) {
//                        if (starts.process != null) {
//                            starts.process.destroy();
//                            //Anzeige ändern - fertig
//                            if (starts.datenDownload.getQuelle() == Starts.QUELLE_BUTTON) {
//                                //für die direkten Starts mit dem Button
//                                starts.status = Starts.STATUS_FERTIG;
//                            } else {
//                                starts.status = Starts.STATUS_INIT;
//                            }
//                            //mit dem flvstreamer könnte man weitermachen, wennd das File noch da wäre
//                            //new File(starts.film.arr[Konstanten.FILM_ZIEL_PFAD_DATEI_NR]).delete();
//                        }
//                    } else { //Exitvalue vom Prozess prüfen und ggf. neu Starten
//                        if (k != 0) {
//                            if (starts.datenDownload.isRestart()) {
//                                //Download wieder starten
//                                if (filesize == -1) {
//                                    //erstes Mal
//                                    File file = new File(starts.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
//                                    if (file.exists()) {
//                                        if (file.length() == 0) {
//                                            // zum Wiederstarten die leere Datei löschen, alles auf Anfang
//                                            Log.systemMeldung(new String[]{"StartenProgramm, Restart, leere Datei löschen", starts.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]});
//                                            try {
//                                                file.delete();
//                                            } catch (Exception ex) {
//                                            }
//                                        }
//                                    }
//                                    if (file.exists()) {
//                                        filesize = file.length();
//                                        startOk = true;
//                                    } else if (starts.startcounter < Starts.STARTCOUNTER_MAX) {
//                                        //counter prüfen und bei einem Maxwert abbrechen, sonst endlos
//                                        startOk = true;
//                                    }
//                                } else {
//                                    //jetzt muss das File wachsen, sonst kein Restart
//                                    File file = new File(starts.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
//                                    if (file.exists()) {
//                                        if (file.length() > filesize) {
//                                            //nur weitermachen wenn die Datei tasächlich wächst
//                                            startOk = true;
//                                            filesize = file.length();
//                                        }
//                                    }
//                                }
//                                if (startOk && starten(" Restart")) {
//                                    restart = true;
//                                } else {
//                                    //Anzeige ändern - fertig mit Fehler
//                                    starts.status = Starts.STATUS_ERR;
//                                }
//                            } else {
//                                //Anzeige ändern - fertig
//                                starts.status = Starts.STATUS_ERR;
//                            }
//                        } else if (starts.datenDownload.getQuelle() == Starts.QUELLE_BUTTON) {
//                            //für die direkten Starts mit dem Button
//                            starts.status = Starts.STATUS_FERTIG;
//                        } else if (pruefen(starts)) {
//                            //Anzeige ändern - fertig
//                            starts.status = Starts.STATUS_FERTIG;
//                        } else {
//                            //Anzeige ändern - fehler
//                            starts.status = Starts.STATUS_ERR;
//                        }
//                    }
//                }
//            } catch (Exception ex) {
//                Log.fehlerMeldung(395623710, "StarterClass.StartenProgramm-2", ex);
//            }
//            fertigmeldung(starts);
//            beiFehlerAufraeumen(starts);
//            starts.datenDownload.startMelden(DatenDownload.PROGRESS_FERTIG);
//            notifyStartEvent();
//        }

    private class StartenDonwnload implements Runnable {

        Start start;

        public StartenDonwnload(Start s) {
            start = s;
            start.status = Start.STATUS_RUN;
            notifyStartEvent();
        }

        @Override
        public void run() {
            startmeldung(start);
            InputStream input;
            OutputStream destStream;
            try {
                int len;
                new File(start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_NR]).mkdirs();
                URL feedUrl = new URL(start.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR]);
                int maxLen = laenge(start.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR]);
                int downLen = 0;
                input = feedUrl.openStream();
                byte[] buffer = new byte[1024];
                destStream = new FileOutputStream(start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
                while ((len = input.read(buffer)) != -1 && !start.stoppen) {
                    downLen += buffer.length;
                    if (maxLen > 0) {
                        long p = (downLen * (long) 1000) / maxLen;
                        // p muss zwischen 1 und 999 liegen
                        if (p == 0) {
                            p = DatenDownload.PROGRESS_GESTARTET;
                        }
                        if (p >= 1000) {
                            p = 999;
                        }
                        start.datenDownload.startMelden((int) p);
                    }
                    destStream.write(buffer, 0, len);
                }
                input.close();
                destStream.close();
            } catch (Exception ex) {
                Log.fehlerMeldung(502039078, "StarterClass.StartenDonwnload-1", ex);
            }
            if (!start.stoppen) {
                if (start.datenDownload.getQuelle() == Start.QUELLE_BUTTON) {
                    // direkter Start mit dem Button
                    start.status = Start.STATUS_FERTIG;
                } else if (pruefen(start)) {
                    //Anzeige ändern - fertig
                    start.status = Start.STATUS_FERTIG;
                } else {
                    //Anzeige ändern - bei Fehler fehlt der Eintrag
                    start.status = Start.STATUS_ERR;
                }
            }
            leeresFileLoeschen(new File(start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]));
            fertigmeldung(start);
            start.datenDownload.startMelden(DatenDownload.PROGRESS_FERTIG);
            notifyStartEvent();
        }
    }

    private int laenge(String url) {
        int ret;
        try {
            URL u = new URL(url);
            ret = u.openConnection().getContentLength();
        } catch (Exception ex) {
            ret = -1;
            Log.fehlerMeldung(643298301, "StarterClass.StartenDonwnload.laenge", ex);
        }
        if (ret < 100) {
            // dann wars nix
            ret = -1;
        }
        return ret;
    }

    private boolean pruefen(Start start) {
        //prüfen ob der Downoad geklappt hat und die Datei existiert und eine min. Grüße hat
        boolean ret = false;
        File file = new File(start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
        if (!file.exists()) {
            Log.fehlerMeldung(550236231, "StartetClass.pruefen-1", "Download fehlgeschlagen: Datei existiert nicht" + start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
        } else if (file.length() < Konstanten.MIN_DATEI_GROESSE_KB * 1024) {
            Log.fehlerMeldung(795632500, "StartetClass.pruefen-2", "Download fehlgeschlagen: Datei zu klein" + start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
        } else {
            if (start.datenDownload.istAbo()) {
                ddaten.erledigteAbos.zeileSchreiben(start.datenDownload.arr[DatenDownload.DOWNLOAD_THEMA_NR],
                        start.datenDownload.arr[DatenDownload.DOWNLOAD_TITEL_NR],
                        start.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR]);
            }
            ret = true;
        }
        return ret;
    }

    private void leeresFileLoeschen(File file) {
        //prüfen ob die Datei existiert und eine min. Grüße hat, wenn nicht, dann löschen
        try {
            if (file.exists()) {
                // zum Wiederstarten/Aufräumen die leer/zu kleine Datei löschen, alles auf Anfang
                if (file.length() == 0) {
                    // zum Wiederstarten/Aufräumen die leer/zu kleine Datei löschen, alles auf Anfang
                    Log.systemMeldung(new String[]{"Restart/Aufräumen: leere Datei löschen", file.getAbsolutePath()});
                    file.delete();
                } else if (file.length() < Konstanten.MIN_DATEI_GROESSE_KB * 1024) {
                    Log.systemMeldung(new String[]{"Restart/Aufräumen: Zu kleine Datei löschen", file.getAbsolutePath()});
                    file.delete();

                }
            }
        } catch (Exception ex) {
            Log.fehlerMeldung(795632500, "StartetClass.leeresFileLoeschen", "Fehler beim löschen" + file.getAbsolutePath());
        }
    }

    private void startmeldung(Start start) {
        ArrayList<String> text = new ArrayList<String>();
        boolean abspielen = start.datenDownload.getQuelle() == Start.QUELLE_BUTTON;
        if (abspielen) {
            text.add("Film starten");
        } else {
            if (start.startcounter > 1) {
                text.add("Download starten - Restart (Summe Starts: " + start.startcounter + ")");
            } else {
                text.add("Download starten");
            }
            text.add("Programmset: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_PROGRAMMSET_NR]);
            text.add("Ziel: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
        }
        text.add("URL: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR]);
        if (start.datenDownload.getArt() == Start.ART_DOWNLOAD) {
            text.add("direkter Download");
        } else {
            text.add("Programmaufruf: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_PROGRAMM_AUFRUF_NR]);
        }
        Log.systemMeldung(text.toArray(new String[]{}));
    }

    private void fertigmeldung(Start start) {
        ArrayList<String> text = new ArrayList<String>();
        boolean abspielen = start.datenDownload.getQuelle() == Start.QUELLE_BUTTON;
        if (abspielen) {
            text.add("Film fertig");
        } else {
            if (start.stoppen) {
                text.add("Download abgebrochen");
            } else if (start.status != Start.STATUS_ERR) {
                // dann ists gut
                text.add("Download ist fertig und hat geklappt");
            } else {
                text.add("Download ist fertig und war fehlerhaft");
            }
            text.add("Programmset: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_PROGRAMMSET_NR]);
            text.add("Ziel: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME_NR]);
        }
        text.add("URL: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_URL_NR]);
        if (start.datenDownload.getArt() == Start.ART_DOWNLOAD) {
            text.add("direkter Download");
        } else {
            text.add("Programmaufruf: " + start.datenDownload.arr[DatenDownload.DOWNLOAD_PROGRAMM_AUFRUF_NR]);
        }
        Log.systemMeldung(text.toArray(new String[]{}));
    }
}
