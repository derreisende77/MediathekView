package mediathek.tool.table;

import mediathek.config.MVConfig;
import mediathek.daten.DatenFilm;
import mediathek.gui.tabs.tab_film.GuiFilme;
import mediathek.tool.ApplicationConfiguration;
import mediathek.tool.FilmSize;
import mediathek.tool.models.TModelFilm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.Comparator;
import java.util.List;

public class MVFilmTable extends ASelectableMVTable {
    private static final Logger logger = LogManager.getLogger();
    private MyRowSorter<TableModel> sorter;

    public MVFilmTable() {
        super();
        setAutoCreateRowSorter(false);

        addPropertyChangeListener("model", evt -> {
            //System.out.println("TABLE MODEL CHANGED");
            if (sorter == null) {
                sorter = new MyRowSorter<>(getModel());
                //sorter.addRowSorterListener(evt1 -> System.out.println("SORT ORDER HAS CHANGED"));
            }
            setRowSorter(sorter);
            sorter.setModel(getModel());
        });

    }

    @Override
    protected void setSelected() {
        boolean found = false;

        if (selIndexes != null) {
            int r;
            selectionModel.setValueIsAdjusting(true);
            final var tModel = (TModelFilm) getModel();
            for (int i : selIndexes) {
                r = tModel.getModelRowForFilmNumber(i);
                if (r >= 0) {
                    // ansonsten gibts die Zeile nicht mehr
                    r = convertRowIndexToView(r);
                    addRowSelectionInterval(r, r);
                    found = true;
                }
            }
            if (!found && selRow >= 0 && this.getRowCount() > selRow) {
                setRowSelectionInterval(selRow,selRow);
            } else if (!found && selRow >= 0 && this.getRowCount() > 0) {
                final var rowCount = tModel.getRowCount() - 1;
                setRowSelectionInterval(rowCount, rowCount);
            }
            selectionModel.setValueIsAdjusting(false);
        }
        selIndexes = null;
    }

    @Override
    protected void loadDefaultFontSize() {
        var config = ApplicationConfiguration.getConfiguration();
        try {
            final var fontSize = config.getFloat(ApplicationConfiguration.TAB_FILM_FONT_SIZE);
            var newFont = getDefaultFont().deriveFont(fontSize);
            setDefaultFont(newFont);
        }
        catch (Exception ignored) {}
    }

    @Override
    protected void saveDefaultFontSize() {
        var config = ApplicationConfiguration.getConfiguration();
        final var fontSize = getDefaultFont().getSize2D();
        config.setProperty(ApplicationConfiguration.TAB_FILM_FONT_SIZE, fontSize);
    }

    @Override
    protected void setupTableType() {
        //logger.debug("setupTableType()");

        maxSpalten = DatenFilm.MAX_ELEM;
        spaltenAnzeigen = getSpaltenEinAus(GuiFilme.VISIBLE_COLUMNS, DatenFilm.MAX_ELEM);
        indexSpalte = DatenFilm.FILM_NR;
        nrDatenSystem = MVConfig.Configs.SYSTEM_EIGENSCHAFTEN_TABELLE_FILME;
        iconAnzeigenStr = MVConfig.Configs.SYSTEM_TAB_FILME_ICON_ANZEIGEN;
        iconKleinStr = MVConfig.Configs.SYSTEM_TAB_FILME_ICON_KLEIN;

        //setModel(new TModelFilm());
    }

    private void resetFilmeTab(int i) {
        //logger.debug("resetFilmeTab()");

        reihe[i] = i;
        breite[i] = 200;
        switch (i) {
            case DatenFilm.FILM_NR -> breite[i] = 75;
            case DatenFilm.FILM_TITEL -> breite[i] = 300;
            case DatenFilm.FILM_DATUM, DatenFilm.FILM_ZEIT, DatenFilm.FILM_SENDER, DatenFilm.FILM_GROESSE,
                    DatenFilm.FILM_DAUER, DatenFilm.FILM_GEO -> breite[i] = 100;
            case DatenFilm.FILM_URL -> breite[i] = 500;
            case DatenFilm.FILM_ABSPIELEN, DatenFilm.FILM_AUFZEICHNEN, DatenFilm.FILM_MERKEN -> breite[i] = 20;
            case DatenFilm.FILM_HD, DatenFilm.FILM_UT -> breite[i] = 50;
        }
    }

    @Override
    public void resetTabelle() {
        //logger.debug("resetTabelle()");

        for (int i = 0; i < maxSpalten; ++i) {
            resetFilmeTab(i);
        }

        getRowSorter().setSortKeys(null); // empty sort keys
        spaltenAusschalten();
        setSpaltenEinAus(breite, spaltenAnzeigen);
        setSpalten();
        setHeight();
    }

    @Override
    protected void spaltenAusschalten() {
        // do nothing here
    }

    @Override
    public void getSpalten() {
        //logger.debug("getSpalten()");

        // Einstellungen der Tabelle merken
        getSelected();

        for (int i = 0; i < reihe.length && i < getModel().getColumnCount(); ++i) {
            reihe[i] = convertColumnIndexToModel(i);
        }

        for (int i = 0; i < breite.length && i < getModel().getColumnCount(); ++i) {
            breite[i] = getColumnModel().getColumn(convertColumnIndexToView(i)).getWidth();
        }

        // save sortKeys
        var rowSorter = getRowSorter();
        if (rowSorter != null) {
            listeSortKeys = rowSorter.getSortKeys();
        } else {
            listeSortKeys = null;
        }
    }

    private void reorderColumns()
    {
        final TableColumnModel model = getColumnModel();
        var numCols = getColumnCount();
        for (int i = 0; i < reihe.length && i < numCols; ++i) {
            //move only when there are changes...
            if (reihe[i] != i)
                model.moveColumn(convertColumnIndexToView(reihe[i]), i);
        }
    }

    private void restoreSortKeys()
    {
        if (listeSortKeys != null) {
            var rowSorter = getRowSorter();
            var tblSortKeys = rowSorter.getSortKeys();
            if (!(listeSortKeys == tblSortKeys)) {
                if (!listeSortKeys.isEmpty()) {
                    rowSorter.setSortKeys(listeSortKeys);
                }
            }
        }
    }

    /**
     * Setzt die gemerkte Position der Spalten in der Tabelle wieder.
     * Ziemlich ineffizient!
     */
    @Override
    public void setSpalten() {
        //logger.debug("setSpalten()");
        try {
            changeColumnWidth();

            changeColumnWidth2();

            reorderColumns();

            restoreSortKeys();

            setSelected();

            validate();
        } catch (Exception ex) {
            logger.error("setSpalten", ex);
        }
    }

    static class MyRowSorter<M extends TableModel> extends TableRowSorter<M> {
        //private static final Logger rsLogger = LogManager.getLogger();

        public MyRowSorter(M model) {
            super(model);
        }

        @Override
        public void setModel(M model) {
            super.setModel(model);
            // do not sort buttons
            setSortable(DatenFilm.FILM_ABSPIELEN, false);
            setSortable(DatenFilm.FILM_AUFZEICHNEN, false);
            //compare to FilmSize->int instead of String
            setComparator(DatenFilm.FILM_GROESSE, (Comparator<FilmSize>) FilmSize::compareTo);
            // deactivate german collator used in DatenFilm as it slows down sorting as hell...
            setComparator(DatenFilm.FILM_SENDER, (Comparator<String>) String::compareTo);
            setComparator(DatenFilm.FILM_ZEIT, (Comparator<String>) String::compareTo);
            setComparator(DatenFilm.FILM_URL, (Comparator<String>) String::compareTo);
        }

        @Override
        public void setSortKeys(List<? extends SortKey> sortKeys) {
            //FIXME something is wrong in MVTable with setting sort keys
            if (sortKeys != null) {
                if (sortKeys.size() > 1) {
                    //rsLogger.error("BULLSHIT SORTKEYS IN");
                    sortKeys.remove(1);
                }
            }
            super.setSortKeys(sortKeys);
/*
            var list = getSortKeys();
            if (list != null) {
                rsLogger.debug("SORT KEYS:");
                for (var key : list) {
                    rsLogger.debug("COLUMN: " + key.getColumn() + ",SortOrder: " + key.getSortOrder().toString());
                }
            }
*/
        }
    }
}
