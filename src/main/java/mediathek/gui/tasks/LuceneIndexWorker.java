package mediathek.gui.tasks;

import com.google.common.base.Stopwatch;
import mediathek.config.Daten;
import mediathek.daten.DatenFilm;
import mediathek.daten.IndexedFilmList;
import mediathek.tool.datum.DateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;

public class LuceneIndexWorker extends SwingWorker<Void, Void> {
    private static final Logger logger = LogManager.getLogger();
    private final JProgressBar progressBar;
    private final JLabel progLabel;
    private int oldProgress = 0;

    public LuceneIndexWorker(@NotNull JLabel progLabel, @NotNull JProgressBar progressBar) {
        this.progressBar = progressBar;
        this.progLabel = progLabel;

        SwingUtilities.invokeLater(() -> {
            progLabel.setText("Blacklist anwenden");
            progressBar.setIndeterminate(true);
        });
    }

    private void indexFilm(@NotNull IndexWriter writer, @NotNull DatenFilm film) throws IOException {
        var doc = new Document();
        // store fields for debugging, otherwise they should stay disabled
        doc.add(new StringField(LuceneIndexKeys.ID, Integer.toString(film.getFilmNr()), Field.Store.YES));
        if (film.isNew()) {
            doc.add(new StringField(LuceneIndexKeys.NEW, Boolean.toString(true), Field.Store.NO));
        }
        doc.add(new TextField(LuceneIndexKeys.SENDER, film.getSender(), Field.Store.NO));
        doc.add(new TextField(LuceneIndexKeys.TITEL, film.getTitle(), Field.Store.NO));
        doc.add(new TextField(LuceneIndexKeys.THEMA, film.getThema(), Field.Store.NO));
        if (!film.getDescription().isEmpty()) {
            doc.add(new TextField(LuceneIndexKeys.BESCHREIBUNG, film.getDescription(), Field.Store.NO));
        }
        if (film.isLivestream()) {
            doc.add(new StringField(LuceneIndexKeys.LIVESTREAM, Boolean.toString(true), Field.Store.NO));
        }
        if (film.isHighQuality()) {
            doc.add(new StringField(LuceneIndexKeys.HIGH_QUALITY, Boolean.toString(true), Field.Store.NO));
        }
        if (film.hasSubtitle() || film.hasBurnedInSubtitles()) {
            doc.add(new StringField(LuceneIndexKeys.SUBTITLE, Boolean.toString(true), Field.Store.NO));
        }
        if (film.isTrailerTeaser()) {
            doc.add(new StringField(LuceneIndexKeys.TRAILER_TEASER, Boolean.toString(true), Field.Store.NO));
        }
        if (film.isAudioVersion()) {
            doc.add(new StringField(LuceneIndexKeys.AUDIOVERSION, Boolean.toString(true), Field.Store.NO));
        }
        if (film.isSignLanguage()) {
            doc.add(new StringField(LuceneIndexKeys.SIGN_LANGUAGE, Boolean.toString(true), Field.Store.NO));
        }

        try {
            String sendeDatumStr = DateTools.timeToString(DateUtil.convertFilmDateToLuceneDate(film),
                    DateTools.Resolution.DAY);
            doc.add(new StringField(LuceneIndexKeys.SENDE_DATUM, sendeDatumStr, Field.Store.NO));
        } catch (Exception ex) {
            logger.error("Error indexing sendedatum", ex);
        }
        writer.addDocument(doc);
    }

    @Override
    protected Void doInBackground() {
        var filmListe = (IndexedFilmList) Daten.getInstance().getListeFilmeNachBlackList();
        SwingUtilities.invokeLater(() -> {
            progLabel.setText("Indiziere Filme");
            progressBar.setMinimum(0);
            progressBar.setMaximum(100);
            progressBar.setValue(0);
            progressBar.setIndeterminate(false);
        });

        //index filmlist after blacklist only
        var writer = filmListe.getWriter();
        var totalSize = (float) filmListe.size();

        try {
            int counter = 0;
            Stopwatch watch = Stopwatch.createStarted();
            //for safety delete all entries
            writer.deleteAll();

            for (var film : filmListe) {
                counter++;
                indexFilm(writer, film);

                final var progress = (int) (100.0f * (counter / totalSize));
                if (progress != oldProgress) {
                    oldProgress = progress;
                    SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                }
            }
            writer.commit();
            watch.stop();
            logger.trace("Lucene index creation took {}", watch);

            var reader = filmListe.getReader();
            if (reader != null) {
                reader.close();
            }
            reader = DirectoryReader.open(filmListe.getLuceneDirectory());
            filmListe.setReader(reader);

            filmListe.setIndexSearcher(new IndexSearcher(reader));
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(98);
        }

        return null;
    }

}
