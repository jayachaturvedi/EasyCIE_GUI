package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.DeterminantValueSet;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.core.TypeDefinition;
import edu.utah.bmi.nlp.fastcner.uima.FastCNER_AE_General;
import edu.utah.bmi.nlp.fastcontext.uima.FastContext_General_AE;
import edu.utah.bmi.nlp.fastner.uima.FastNER_AE_General;
import edu.utah.bmi.nlp.rush.uima.RuSH_AE;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.type.system.Doc_Base;
import edu.utah.bmi.nlp.type.system.SectionHeader;
import edu.utah.bmi.nlp.type.system.SentenceOdd;
import edu.utah.bmi.nlp.uima.*;
import edu.utah.bmi.nlp.uima.ae.AnnotationFeatureMergerAnnotator;
import edu.utah.bmi.nlp.uima.ae.AnnotationPrinter;
import edu.utah.bmi.nlp.uima.ae.DocInferenceAnnotator;
import edu.utah.bmi.nlp.uima.ae.FeatureInferenceAnnotator;
import edu.utah.bmi.nlp.uima.loggers.ConsoleLogger;
import edu.utah.bmi.nlp.uima.loggers.UIMALogger;
import edu.utah.bmi.nlp.uima.reader.SQLTextReader;
import edu.utah.bmi.nlp.uima.writer.BratWritter_AE;
import edu.utah.bmi.nlp.uima.writer.EhostWriter_AE;
import edu.utah.bmi.nlp.uima.writer.SQLWriterCasConsumer;
import edu.utah.bmi.nlp.uima.writer.XMIWritter_AE;
import edu.utah.bmi.sectiondectector.SectionDetectorR_AE;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Created by Jianlin Shi on 9/19/16.
 */
public class RunEasyCIE extends GUITask {
    public static Logger logger = Logger.getLogger(RunEasyCIE.class.getCanonicalName());
    protected String readDBConfigFile, writeConfigFileName, inputTableName, outputTableName,
            ehostDir, bratDir, xmiDir, annotator, datasetId;
    public boolean report = false, fastNERCaseSensitive = true;
    protected String sectionRule = "", rushRule = "", fastNERRule = "", fastCNERRule = "", includesections = "", contextRule = "",
            featureInfRule = "", featureMergerRule = "", docInfRule = "";
    public AdaptableUIMACPETaskRunner runner;
    protected DAO rdao, wdao;
    public boolean ehost = false, brat = false, xmi = false;
    protected String exporttypes;
    protected String customTypeDescriptor;

    public RunEasyCIE() {

    }


    public RunEasyCIE(TasksFX tasks) {
        initiate(tasks, "db");
    }

    public RunEasyCIE(TasksFX tasks, String paras) {
        if (paras == null || paras.length() == 0)
            initiate(tasks, "db");
        initiate(tasks, paras);
    }

    public void init(GUITask task, String annotator, String rushRule, String fastNERRule, String fastCNERRule, String contextRule,
                     String annotationInferenceRule, String docInferenceRule, boolean report, boolean fastNerCaseSensitive,
                     String readDBConfigFile, String inputTableName, String datasetId, String writeConfigFileName,
                     String outputTableName, String ehostDir, String bratDir, String xmiDir, String exporttypes, String option) {
        this.annotator = annotator;
        this.rushRule = rushRule;
        this.fastNERRule = fastNERRule;
        this.fastCNERRule = fastCNERRule;
        this.contextRule = contextRule;
        this.featureInfRule = annotationInferenceRule;
        this.docInfRule = docInferenceRule;
        this.report = report;
        this.fastNERCaseSensitive = fastNerCaseSensitive;
        this.readDBConfigFile = readDBConfigFile;
        this.inputTableName = inputTableName;
        this.datasetId = datasetId;
        this.writeConfigFileName = writeConfigFileName;
        this.outputTableName = outputTableName;
        this.ehostDir = ehostDir;
        this.bratDir = bratDir;
        this.xmiDir = xmiDir;
        this.exporttypes = exporttypes.replaceAll("\\s+", "");
        switch (option) {
            case "ehost":
                ehost = true;
                break;
            case "brat":
                brat = true;
                break;
            case "xmi":
                xmi = true;
                break;
            default:
                ehost = false;
                brat = false;
                xmi = false;
        }
        if (ehostDir == null || ehostDir.length() == 0)
            ehost = false;
        if (bratDir == null || bratDir.length() == 0)
            brat = false;
        if (xmiDir == null || xmiDir.length() == 0)
            xmi = false;
        initPipe(task, readDBConfigFile, datasetId, annotator);
    }

    protected void initiate(TasksFX tasks, String option) {
        if (!Platform.isFxApplicationThread()) {
            guiEnabled = false;
        }
        if (System.getProperty("java.util.logging.config.file") == null &&
                new File("logging.properties").exists()) {
            System.setProperty("java.util.logging.config.file", "logging.properties");
        }
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream(new File("logging.properties")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateGUIMessage("Initiate configurations..");
        TaskFX config = tasks.getTask(ConfigKeys.maintask);
        annotator = config.getValue(ConfigKeys.annotator);
        sectionRule = config.getValue(ConfigKeys.sectionRule);
        fastNERRule = config.getValue(ConfigKeys.tRuleFile);

        fastCNERRule = config.getValue(ConfigKeys.cRuleFile);

        includesections = config.getValue(ConfigKeys.includesections);
        contextRule = config.getValue(ConfigKeys.contextRule);
        featureInfRule = config.getValue(ConfigKeys.featureInfRule);
        featureMergerRule = config.getValue(ConfigKeys.featureMergerRule);
        docInfRule = config.getValue(ConfigKeys.docInfRule);

        String reportString = config.getValue(ConfigKeys.reportPreannotating);
        report = reportString.length() > 0 && (reportString.charAt(0) == 't' || reportString.charAt(0) == 'T' || reportString.charAt(0) == '1');
        reportString = config.getValue(ConfigKeys.fastNerCaseSensitive);
        fastNERCaseSensitive = reportString.length() > 0 && (reportString.charAt(0) == 't' || reportString.charAt(0) == 'T' || reportString.charAt(0) == '1');


        config = tasks.getTask("settings");
        readDBConfigFile = config.getValue(ConfigKeys.readDBConfigFile);
        inputTableName = config.getValue(ConfigKeys.inputTableName);
        datasetId = config.getValue(ConfigKeys.datasetId);
        writeConfigFileName = config.getValue(ConfigKeys.writeConfigFileName);
        outputTableName = config.getValue(ConfigKeys.outputTableName);
        rushRule = config.getValue(ConfigKeys.rushRule);


        TaskFX exportConfig = tasks.getTask("export");
        ehostDir = exportConfig.getValue(ConfigKeys.outputEhostDir);
        bratDir = exportConfig.getValue(ConfigKeys.outputBratDir);
        xmiDir = exportConfig.getValue(ConfigKeys.outputXMIDir);
        exporttypes = exportConfig.getValue(ConfigKeys.exportTypes);

        switch (option) {
            case "ehost":
                ehost = true;
                break;
            case "brat":
                brat = true;
                break;
            case "xmi":
                xmi = true;
                break;
            default:
                ehost = false;
                brat = false;
                xmi = false;
        }

        initPipe(this, readDBConfigFile, datasetId, annotator);

    }

    @Override
    protected Object call() throws Exception {
        runner.run();
        return null;
    }


    protected void initPipe(GUITask task, String readDBConfigFile, String datasetId, String annotator) {
        rdao = new DAO(new File(readDBConfigFile), false, false);
        if (writeConfigFileName.equals(readDBConfigFile)) {
            wdao = rdao;
        } else {
            File writeFile = new File(writeConfigFileName);
            if (writeFile.exists() && writeFile.isFile() &&
                    (writeConfigFileName.toLowerCase().endsWith(".xml")) ||
                    (writeConfigFileName.toLowerCase().endsWith(".json")))
                wdao = new DAO(new File(writeConfigFileName));
        }
        UIMALogger logger = addLogger(wdao, annotator);
        logger.logStartTime();
        String runId = logger.getRunid() + "";


        String defaultTypeDescriptor = "desc/type/All_Types";
//        JXTransformer jxTransformer;
        customTypeDescriptor = "desc/type/pipeline_" + annotator;

        if (new File(customTypeDescriptor + ".xml").exists())
            runner = new AdaptableUIMACPETaskRunner(customTypeDescriptor, "./classes/");
        else
            runner = new AdaptableUIMACPETaskRunner(defaultTypeDescriptor, "./classes/");
        runner.setLogger(logger);
        runner.setTask(task);

        initTypes(customTypeDescriptor);
        addReader(readDBConfigFile, datasetId);
        addAnalysisEngines(runner);
//        SQLWriterCasConsumer.debug=true;

        addWriter(runId, annotator);
    }


    protected UIMALogger addLogger(DAO dao, String annotator) {
        if (logger.isLoggable(Level.FINE))
            return new ConsoleLogger();
        else
            return new NLPDBLogger(dao, "LOG", "RUN_ID", annotator);
    }

    /**
     * Read through all the annotations, iterate all the types and features,
     * check to see if the type descriptor has included all of them, if not create
     * the missed types and features
     */
    protected void initTypes(String customTypeDescriptor) {

        if (sectionRule.length() > 0)
            runner.addConceptTypes(SectionDetectorR_AE.getTypeDefinitions(sectionRule).values());

        if (fastNERRule.length() > 0)
            runner.addConceptTypes(FastNER_AE_General.getTypeDefinitions(fastNERRule, fastNERCaseSensitive).values());

        if (fastCNERRule.length() > 0)
            runner.addConceptTypes(FastCNER_AE_General.getTypeDefinitions(fastCNERRule, true).values());

        if (contextRule.length() > 0)
            runner.addConceptTypes(FastContext_General_AE.getTypeDefinitions(contextRule, false).values());

        if (featureInfRule.length() > 0)
            runner.addConceptTypes(FeatureInferenceAnnotator.getTypeDefinitions(featureInfRule).values());

        if (featureMergerRule.length() > 0)
            runner.addConceptTypes(AnnotationFeatureMergerAnnotator.getTypeDefinitions(featureMergerRule).values());

        if (docInfRule.length() > 0)
            runner.addConceptTypes(DocInferenceAnnotator.getTypeDefinitions(docInfRule).values());
        runner.reInitTypeSystem(customTypeDescriptor);
    }

    public void initTypes(Collection<TypeDefinition> typeDefinitions) {
        runner.addConceptTypes(typeDefinitions);
        runner.reInitTypeSystem(customTypeDescriptor);
    }

    public void setReader(Class readerClass, Object[] configurations) {
        runner.setCollectionReader(readerClass, configurations);
    }

    public void addReader(String readDBConfigFile, String datasetId) {
        SQLTextReader.dao = rdao;
        runner.setCollectionReader(SQLTextReader.class, new Object[]{SQLTextReader.PARAM_DB_CONFIG_FILE, readDBConfigFile,
                SQLTextReader.PARAM_DATASET_ID, datasetId,
                SQLTextReader.PARAM_DOC_TABLE_NAME, inputTableName,
                SQLTextReader.PARAM_QUERY_SQL_NAME, "masterInputQuery",
                SQLTextReader.PARAM_COUNT_SQL_NAME, "masterCountQuery",
                SQLTextReader.PARAM_DOC_COLUMN_NAME, "TEXT"});
    }


    public void addWriter(String runId, String annotator) {
        File output = new File(writeConfigFileName);
        try {
            if (!output.exists()) {
                FileUtils.forceMkdir(output);
            } else if (output.isFile()) {
                output = new File("data/output");
                if (!output.exists())
                    FileUtils.forceMkdir(output.getParentFile());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (ehost)
            runner.addAnalysisEngine(EhostWriter_AE.class,
                    new Object[]{EhostWriter_AE.PARAM_OUTPUTDIR, new File(output, "ehost").getAbsolutePath(),
                            EhostWriter_AE.PARAM_ANNOTATOR, annotator,
                            EhostWriter_AE.PARAM_UIMATYPES, exporttypes});
        if (brat)
            runner.addAnalysisEngine(BratWritter_AE.class,
                    new Object[]{BratWritter_AE.PARAM_OUTPUTDIR, new File(output, "brat").getAbsolutePath(),
                            BratWritter_AE.PARAM_ANNOTATOR, annotator,
                            BratWritter_AE.PARAM_UIMATYPES, exporttypes});
        if (xmi)
            runner.addAnalysisEngine(XMIWritter_AE.class,
                    new Object[]{XMIWritter_AE.PARAM_OUTPUTDIR, new File(output, "xmi").getAbsolutePath(),
                            "Annotator", annotator,
                            XMIWritter_AE.PARAM_UIMATYPES, exporttypes});

        if (!(ehost || brat || xmi)) {
            SQLWriterCasConsumer.dao = wdao;
            if (!wdao.checkExits("checkAnnotatorExist", annotator)) {
                wdao.insertRecord("ANNOTATORS", new RecordRow(annotator));
            }
            runner.addAnalysisEngine(SQLWriterCasConsumer.class, new Object[]{
                    SQLWriterCasConsumer.PARAM_SQLFILE, writeConfigFileName,
                    SQLWriterCasConsumer.PARAM_TABLENAME, outputTableName,
                    SQLWriterCasConsumer.PARAM_ANNOTATOR, annotator,
                    SQLWriterCasConsumer.PARAM_VERSION, runId,
                    SQLWriterCasConsumer.PARAM_WRITE_CONCEPT, exporttypes,
                    SQLWriterCasConsumer.PARAM_OVERWRITETABLE, false, SQLWriterCasConsumer.PARAM_BATCHSIZE, 150});
        }

    }

    protected void addAnalysisEngines(AdaptableUIMACPETaskRunner runner) {
        if (rushRule.length() > 0) {
            logger.finer("add engine RuSH_AE");
            if (exporttypes == null || exporttypes.indexOf("Sentence") == -1)
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true});
            else
                runner.addAnalysisEngine(RuSH_AE.class, new Object[]{RuSH_AE.PARAM_RULE_STR, rushRule,
                        RuSH_AE.PARAM_INCLUDE_PUNCTUATION, true,
                        RuSH_AE.PARAM_ALTER_SENTENCE_TYPE_NAME, SentenceOdd.class.getCanonicalName()});
//			if (debug)
//				runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
//						DeterminantValueSet.defaultNameSpace + "Sentence"});
        }

        if (sectionRule.length() > 0) {
            logger.finer("add engine SectionDetectorR_AE");
            runner.addAnalysisEngine(SectionDetectorR_AE.class, new Object[]{SectionDetectorR_AE.PARAM_RULE_FILE_OR_STR, sectionRule});
            if (logger.isLoggable(Level.FINE))
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        SectionHeader.class.getCanonicalName(), AnnotationPrinter.PARAM_INDICATION, "After sectiondetector"});
        }


        if (fastCNERRule.length() > 0) {
            logger.finer("add engine FastCNER_AE_General");
            runner.addAnalysisEngine(FastCNER_AE_General.class, new Object[]{FastCNER_AE_General.PARAM_RULE_FILE_OR_STR, fastCNERRule,
                    FastCNER_AE_General.PARAM_MARK_PSEUDO, false});
            if (logger.isLoggable(Level.FINE)) {
                logger.finer("add engine FastNER_AE_Diff_SP_Concepts");
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FastCNER_AE_General\n"});
            }
        }

        if (fastNERRule.length() > 0) {
            logger.finer("add engine FastNER_AE_General");
            runner.addAnalysisEngine(FastNER_AE_General.class, new Object[]{FastNER_AE_General.PARAM_RULE_FILE_OR_STR, fastNERRule,
                    FastNER_AE_General.PARAM_MARK_PSEUDO, true, FastNER_AE_General.PARAM_CASE_SENSITIVE, fastNERCaseSensitive,
                    FastNER_AE_General.PARAM_INCLUDE_SECTIONS, includesections});
            if (logger.isLoggable(Level.FINE)) {
                logger.finer("add engine FastNER_AE_Diff_SP_Concepts");
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FastNER_AE_General\n"});
            }
        }

        logger.finer("add engine CoordinateNERResults_AE");
        runner.addAnalysisEngine(CoordinateNERResults_AE.class, null);


//        System.out.println("Read Context rules from " + contextRule);
        if (contextRule.length() > 0) {
            logger.finer("add engine FastContext_General_AE ");
            runner.addAnalysisEngine(FastContext_General_AE.class, new Object[]{FastContext_General_AE.PARAM_CONTEXT_RULES_STR, contextRule});
            if (logger.isLoggable(Level.FINE)) {
                logger.finer("print final Concepts");
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FastContext_General_AE\n"});
            }
        }

        if (featureInfRule.length() > 0) {
            logger.finer("add engine FeatureInferenceAnnotator");

            runner.addAnalysisEngine(FeatureInferenceAnnotator.class, new Object[]{
                    FeatureInferenceAnnotator.PARAM_INFERENCE_STR, featureInfRule});
            if (logger.isLoggable(Level.FINE)) {
                logger.finer("print annotation inferenced Concepts");
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After FeatureInferenceAnnotator\n"});
            }
        }

        if (featureMergerRule.length() > 0) {
            logger.finer("add engine FeatureInferenceAnnotator");

            runner.addAnalysisEngine(AnnotationFeatureMergerAnnotator.class, new Object[]{
                    AnnotationFeatureMergerAnnotator.PARAM_INFERENCE_STR, featureMergerRule,
                    AnnotationFeatureMergerAnnotator.PARAM_IN_SITU, false});
            if (logger.isLoggable(Level.FINE)) {
                logger.finer("print annotation inferenced Concepts");
                runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME,
                        DeterminantValueSet.defaultNameSpace + "Concept",
                        AnnotationPrinter.PARAM_INDICATION, "After AnnotationFeatureMergerAnnotator\n"});
            }
        }

        if (docInfRule.length() > 0) {
            logger.finer("add engine DocInferenceAnnotator");
            runner.addAnalysisEngine(DocInferenceAnnotator.class, new Object[]{DocInferenceAnnotator.PARAM_INFERENCE_STR, docInfRule});
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.finer("print final Doc Concepts");
            runner.addAnalysisEngine(AnnotationPrinter.class, new Object[]{AnnotationPrinter.PARAM_TYPE_NAME, Doc_Base.class.getCanonicalName(),
                    AnnotationPrinter.PARAM_INDICATION, "After DocInferenceAnnotator\n"});
        }
    }
}
