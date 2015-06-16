package edu.bu.android.hiddendata;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;

import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.source.AndroidSourceSinkManager.LayoutMatchingMode;
import soot.jimple.infoflow.data.pathBuilders.DefaultPathBuilderFactory.PathBuilder;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import edu.bu.android.hiddendata.infoflow.RewireFlow;

public class FindHidden {
	
	public enum Mode {
		NETWORK_TO_DESERIALIZE,
		DESERIALIZE_TO_UI
	}
	
	private static final Logger logger = LoggerFactory.getLogger(FindHidden.class.getName());
	static String command;
	static boolean generate = false;
	
	private static int timeout = -1;
	private static int sysTimeout = -1;
	
	protected static final String RESULT_DIRECTORY = "results-1";
	protected static final String SOURCESINK_SUFFEX = "-sources_sinks.txt";
	protected static final String EASY_TAINT_WRAPPER_FILE_PREFIX = "-easytaintwrapper.txt";
	protected static final String RESULTS_SUFFIX = "-results.json";
	
	private static String injectionsFilePath = null;
	private static String sourcesAndSinksFilePath = "SourcesAndSinks.txt";
	private static String easyTaintFilePath = null;
	private static boolean stopAfterFirstFlow = false;
	private static boolean implicitFlows = false;
	private static boolean staticTracking = true;
	private static boolean enableCallbacks = true;
	private static boolean enableExceptions = true;
	private static int accessPathLength = 5;
	private static LayoutMatchingMode layoutMatchingMode = LayoutMatchingMode.MatchSensitiveOnly;
	private static boolean flowSensitiveAliasing = true;
	private static boolean computeResultPaths = true;
	private static boolean aggressiveTaintWrapper = false;
	private static boolean librarySummaryTaintWrapper = false;
	private static String summaryPath = "";
	private static PathBuilder pathBuilder = PathBuilder.ContextInsensitiveSourceFinder;
	private static boolean useFragments = false;

	private static CallgraphAlgorithm callgraphAlgorithm = CallgraphAlgorithm.AutomaticSelection;
	
	private static boolean DEBUG = false;
	
	
	private static Mode mode;


	
	/**
	 * @param args Program arguments. args[0] = path to apk-file,
	 * args[1] = path to android-dir (path/android-platforms/)
	 */
	public static void main(final String[] args) throws IOException, InterruptedException {
		if (args.length < 2) {
			printUsage();	
			return;
		}
		//start with cleanup:
		File outputDir = new File("JimpleOutput");
		if (outputDir.isDirectory()){
			boolean success = true;
			for(File f : outputDir.listFiles()){
				success = success && f.delete();
			}
			if(!success){
				System.err.println("Cleanup of output directory "+ outputDir + " failed!");
			}
			outputDir.delete();
		}
		
		// Parse additional command-line arguments
		if (!parseAdditionalOptions(args))
			return;
		if (!validateAdditionalOptions())
			return;
		
		List<String> apkFiles = new ArrayList<String>();
		File apkFile = new File(args[0]);
		if (apkFile.isDirectory()) {
			String[] dirFiles = apkFile.list(new FilenameFilter() {
			
				@Override
				public boolean accept(File dir, String name) {
					return (name.endsWith(".apk"));
				}
			
			});
			for (String s : dirFiles) {
				
				apkFiles.add(new File(args[0], s).getAbsolutePath());
			}
		} else {
			String extension = apkFile.getName().substring(apkFile.getName().lastIndexOf("."));

			if (extension.equalsIgnoreCase(".txt")) {
				BufferedReader rdr = new BufferedReader(new FileReader(apkFile));
				String line = null;
				while ((line = rdr.readLine()) != null)
					apkFiles.add(line);
				rdr.close();
			}
			else if (extension.equalsIgnoreCase(".apk"))
				apkFiles.add(args[0]);
			else {
				System.err.println("Invalid input file format: " + extension);
				return;
			}
		}

		for (final String fullFilePath : apkFiles) {
			//final String fullFilePath;
			
			// Directory handling
			if (apkFiles.size() > 1) {
				/*
				if (apkFile.isDirectory()) {
					fullFilePath = args[0] + File.separator + fileName;
				} else {
					fullFilePath = fileName;
				}
				*/
				System.out.println("Analyzing file " + fullFilePath + "...");
				File flagFile = new File("_Run_" + new File(fullFilePath).getName());
				
				if (flagFile.exists())
					continue;
				flagFile.createNewFile();
				
			} 
			
			//If we have a sys timeout then we need to spawn 
			//a new processes and it will restart this for a single app
			if (sysTimeout > 0) {
				logger.info("Path: " + fullFilePath + " arg1: " + args[1]);
				runAnalysisSysTimeout(fullFilePath, args[1]);
				continue;
			}
			//Set the source sink file to be used
			String apkFileName = new File(fullFilePath).getName();
			String sourceAndSinkFileName = apkFileName + SOURCESINK_SUFFEX;
			String easyTaintFileName = apkFileName + EASY_TAINT_WRAPPER_FILE_PREFIX;

			File apkResult1Dir = new File("./" + RESULT_DIRECTORY + "/" + apkFileName);
			
			//If the file already exists then do the second pass
			if (apkResult1Dir.exists()){
				mode = Mode.DESERIALIZE_TO_UI;
			} else {
				apkResult1Dir.mkdirs();
				mode = Mode.NETWORK_TO_DESERIALIZE;
			}
			
			
			
			
			
			AnalysisResults results = null;
			
			File sourceSinkFileList = new File(apkResult1Dir, apkFileName + "-sources_sinks2.txt");

			File sourceSinkFileDeserializeToUI = new File(apkResult1Dir, sourceAndSinkFileName);
			File easyTaintFileDeserializeToUI  = new File(apkResult1Dir, easyTaintFileName );
			File resultsFile = new File(apkResult1Dir, apkFileName + FindHidden.RESULTS_SUFFIX );

			final long beforeRun = System.nanoTime();

			switch (mode){
				case NETWORK_TO_DESERIALIZE: {
					
					logger.info("=========================================");
					logger.info("Pass 1: Network to deserializer");
					logger.info("=========================================");

					//Keep the source sink and easy taint default
					Infoflow.setPathAgnosticResults(true);

					logger.info("Using " + sourcesAndSinksFilePath + " as source-sink file.");

					// Run the analysis using defaults 
					//results = run(fullFilePath, args[1]);
					results = runAnalysis(fullFilePath, args[1]);
					
					if (results.infoFlowResults == null){
						logger.error("Could not find any flows from Network to Deserialize, cannot continue");
						return;
					}
					
					//Create source sink for next pass as well
					NetworkToDeserializeFlowAnalyzer pass1 = new NetworkToDeserializeFlowAnalyzer(results.context,  sourceSinkFileDeserializeToUI, sourceSinkFileList, easyTaintFileDeserializeToUI,  results.infoFlowResults);
					pass1.process();
					

					System.gc();

					//Only do an additional pass if there were some instances where the model classes were added to lists
					//if (!pass1.getModelToAddSignatureMapping().isEmpty()){
						
						logger.info("=========================================");
						logger.info("Pass 1.5: Lists to List.add");
						logger.info("=========================================");

						//Note: I dont believe this needs agnositic should be false because
						//we only need to location the source here for the injection
						sourcesAndSinksFilePath = sourceSinkFileList.getAbsolutePath();
						easyTaintFilePath = easyTaintFileDeserializeToUI.getAbsolutePath();

						results = runAnalysis(fullFilePath, args[1]);
						ListAnalyzer la = new ListAnalyzer(results.context, results.infoFlowResults, pass1, resultsFile);
						la.process();
						System.gc();
					//} else {
					//	logger.warn("No models were found being added to lists");
					//}

				}
				
				case DESERIALIZE_TO_UI: {
					logger.info("=========================================");
					logger.info("Pass 2: Deserializer to UI");
					logger.info("=========================================");


					Infoflow.setPathAgnosticResults(false);
					injectionsFilePath = resultsFile.getAbsolutePath();
					
					sourcesAndSinksFilePath = sourceSinkFileDeserializeToUI.getAbsolutePath();
					easyTaintFilePath = easyTaintFileDeserializeToUI.getAbsolutePath();

					logger.info("Using " + sourcesAndSinksFilePath + " as source-sink file.");

					// Run the analysis
					results = runAnalysis(fullFilePath, args[1]);
					
					//Now we are done so we need to figure out where we didnt have mappings
					new DeserializeToUiFlowAnalyzer(resultsFile, results.context, results.infoFlowResults).findHiddenData();
						
				}
			}

			System.out.println("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds");

			System.gc();

			
		} //end for 
	}
	private static AnalysisResults run(String path, String args){
		AnalysisResults results = null;
		if (timeout > 0)
			runAnalysisTimeout(path, args);
		else if (sysTimeout > 0)
			runAnalysisSysTimeout(path, args);
		else
			results = runAnalysis(path, args);
		
		return results;
	}
	
	
	/**
	 * When callbacks are enabled flows that are not intentional are found. I think this is due
	 * to the callbacks acting as seeds for the source/sink so here we just want to make sure that
	 * the source is actually coming from one of our defined sources not precomputed by soot
	 * @param sourcesSinks
	 * @param results
	 
	private void postProcessResults(ISourceSinkManager sourcesSinks, InfoflowResults results){
		
		
		for (Entry<ResultSinkInfo, Set<ResultSourceInfo>> entry : results.getResults().entrySet()) {
			SootClass declaringClass = iCfg.getMethodOf(entry.getKey().getSink()).getDeclaringClass();
			entry.getKey().setDeclaringClass(declaringClass);
			
			for (ResultSourceInfo source : entry.getValue()) {
				logger.info("- {} in method {}",source, iCfg.getMethodOf(source.getSource()).getSignature());
				if (source.getPath() != null && !source.getPath().isEmpty()) {
					logger.info("\ton Path: ");
					for (Unit p : source.getPath()) {
						logger.info("\t -> " + iCfg.getMethodOf(p));
						logger.info("\t\t -> " + p);
					}
				}
			}
		}
	}
*/

	private static boolean parseAdditionalOptions(String[] args) {
		int i = 2;
		while (i < args.length) {
			if (args[i].equalsIgnoreCase("--timeout")) {
				timeout = Integer.valueOf(args[i+1]);
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--systimeout")) {
				sysTimeout = Integer.valueOf(args[i+1]);
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--singleflow")) {
				stopAfterFirstFlow = true;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--implicit")) {
				implicitFlows = true;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--nostatic")) {
				staticTracking = false;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--aplength")) {
				accessPathLength = Integer.valueOf(args[i+1]);
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--cgalgo")) {
				String algo = args[i+1];
				if (algo.equalsIgnoreCase("AUTO"))
					callgraphAlgorithm = CallgraphAlgorithm.AutomaticSelection;
				else if (algo.equalsIgnoreCase("CHA"))
					callgraphAlgorithm = CallgraphAlgorithm.CHA;
				else if (algo.equalsIgnoreCase("VTA"))
					callgraphAlgorithm = CallgraphAlgorithm.VTA;
				else if (algo.equalsIgnoreCase("RTA"))
					callgraphAlgorithm = CallgraphAlgorithm.RTA;
				else if (algo.equalsIgnoreCase("SPARK"))
					callgraphAlgorithm = CallgraphAlgorithm.SPARK;
				else {
					System.err.println("Invalid callgraph algorithm");
					return false;
				}
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--nocallbacks")) {
				enableCallbacks = false;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--noexceptions")) {
				enableExceptions = false;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--layoutmode")) {
				String algo = args[i+1];
				if (algo.equalsIgnoreCase("NONE"))
					layoutMatchingMode = LayoutMatchingMode.NoMatch;
				else if (algo.equalsIgnoreCase("PWD"))
					layoutMatchingMode = LayoutMatchingMode.MatchSensitiveOnly;
				else if (algo.equalsIgnoreCase("ALL"))
					layoutMatchingMode = LayoutMatchingMode.MatchAll;
				else {
					System.err.println("Invalid layout matching mode");
					return false;
				}
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--aliasflowins")) {
				flowSensitiveAliasing = false;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--nopaths")) {
				computeResultPaths = false;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--aggressivetw")) {
//TODO Pull request, this could never be true before
				aggressiveTaintWrapper = true;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--pathalgo")) {
				String algo = args[i+1];
				if (algo.equalsIgnoreCase("CONTEXTSENSITIVE"))
					pathBuilder = PathBuilder.ContextSensitive;
				else if (algo.equalsIgnoreCase("CONTEXTINSENSITIVE"))
					pathBuilder = PathBuilder.ContextInsensitive;
				else if (algo.equalsIgnoreCase("SOURCESONLY"))
					pathBuilder = PathBuilder.ContextInsensitiveSourceFinder;
				else {
					System.err.println("Invalid path reconstruction algorithm");
					return false;
				}
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--libsumtw")) {
				librarySummaryTaintWrapper = true;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--summarypath")) {
				summaryPath = args[i + 1];
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--fragments")){
				useFragments = true;
				i++;
			}
			else if (args[i].equalsIgnoreCase("--sourcessinks")){
				sourcesAndSinksFilePath = args[i + 1];
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--easytaint")){
				easyTaintFilePath = args[i + 1];
				i += 2;
			}
			else
				i++;
		}
		return true;
	}
	
	private static boolean validateAdditionalOptions() {
		if (timeout > 0 && sysTimeout > 0) {
			return false;
		}
		if (!flowSensitiveAliasing && callgraphAlgorithm != CallgraphAlgorithm.OnDemand
				&& callgraphAlgorithm != CallgraphAlgorithm.AutomaticSelection) {
			System.err.println("Flow-insensitive aliasing can only be configured for callgraph "
					+ "algorithms that support this choice.");
			return false;
		}
		if (librarySummaryTaintWrapper && summaryPath.isEmpty()) {
			System.err.println("Summary path must be specified when using library summaries");
			return false;
		}
		return true;
	}
	
	private static void runAnalysisTimeout(final String fileName, final String androidJar) {
		FutureTask<InfoflowResults> task = new FutureTask<InfoflowResults>(new Callable<InfoflowResults>() {

			@Override
			public InfoflowResults call() throws Exception {
				
				final BufferedWriter wr = new BufferedWriter(new FileWriter("_out_" + new File(fileName).getName() + ".txt"));
				try {
					final long beforeRun = System.nanoTime();
					wr.write("Running data flow analysis...\n");
					final InfoflowResults res = runAnalysis(fileName, androidJar).infoFlowResults;
					wr.write("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds\n");
					
					wr.flush();
					return res;
				}
				finally {
					if (wr != null)
						wr.close();
				}
			}
			
		});
		ExecutorService executor = Executors.newFixedThreadPool(1);
		executor.execute(task);
		
		try {
			System.out.println("Running infoflow task...");
			task.get(timeout, TimeUnit.MINUTES);
		} catch (ExecutionException e) {
			System.err.println("Infoflow computation failed: " + e.getMessage());
			e.printStackTrace();
		} catch (TimeoutException e) {
			System.err.println("Infoflow computation timed out: " + e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.err.println("Infoflow computation interrupted: " + e.getMessage());
			e.printStackTrace();
		}
		
		// Make sure to remove leftovers
		executor.shutdown();		
	}

	private static String easyTaintToString(){
		return easyTaintFilePath == null ? "" : easyTaintFilePath;
	}
	private static void runAnalysisSysTimeout(final String fileName, final String androidJar) {
		String logFileName = "_out-" + new File(fileName).getName() + ".log";

		String classpath = System.getProperty("java.class.path");
		String javaHome = System.getProperty("java.home");
		String executable = "/usr/bin/timeout";
//TODO pull request, this was set to timeout in minutes, docs say seconds
		String[] command = new String[] { executable,
				"-s", "KILL",
				sysTimeout + "s",
				javaHome + "/bin/java",
				"-Dorg.slf4j.simpleLogger.defaultLogLevel=info",
				"-cp", classpath,
				"edu.bu.android.hiddendata.FindHidden",
				fileName,
				androidJar,
				stopAfterFirstFlow ? "--singleflow" : "--nosingleflow",
				useFragments ? "--fragments" : "--nofragments",
				implicitFlows ? "--implicit" : "--noimplicit",
				staticTracking ? "--static" : "--nostatic", 
				"--aplength", Integer.toString(accessPathLength),
				"--cgalgo", callgraphAlgorithmToString(callgraphAlgorithm),
				enableCallbacks ? "--callbacks" : "--nocallbacks",
				enableExceptions ? "--exceptions" : "--noexceptions",
				"--layoutmode", layoutMatchingModeToString(layoutMatchingMode),
				flowSensitiveAliasing ? "--aliasflowsens" : "--aliasflowins",
				computeResultPaths ? "--paths" : "--nopaths",
				aggressiveTaintWrapper ? "--aggressivetw" : "--nonaggressivetw",
				"--pathalgo", pathAlgorithmToString(pathBuilder),
				"--SOURCESSINKS", sourcesAndSinksFilePath,
				"--EASYTAINT", easyTaintToString()
		};
		System.out.println("Running command: " + Arrays.toString(command));
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			//pb.inheritIO();
			
			//All of the proper logs are redirected to error
			File f= new File("_out_" + new File(fileName).getName() + ".txt");
			//pb.redirectOutput(f);
			pb.redirectError(f);//new File("err_" + new File(fileName).getName() + ".txt"));
			Process proc = pb.start();
		
			proc.waitFor();
		} catch (IOException ex) {
			System.err.println("Could not execute timeout command: " + ex.getMessage());
			ex.printStackTrace();
		} catch (InterruptedException ex) {
			System.err.println("Process was interrupted: " + ex.getMessage());
			ex.printStackTrace();
		}
	}
	
	private static String callgraphAlgorithmToString(CallgraphAlgorithm algorihm) {
		switch (algorihm) {
			case AutomaticSelection:
				return "AUTO";
			case CHA:
				return "CHA";
			case VTA:
				return "VTA";
			case RTA:
				return "RTA";
			case SPARK:
				return "SPARK";
			default:
				return "unknown";
		}
	}

	private static String layoutMatchingModeToString(LayoutMatchingMode mode) {
		switch (mode) {
			case NoMatch:
				return "NONE";
			case MatchSensitiveOnly:
				return "PWD";
			case MatchAll:
				return "ALL";
			default:
				return "unknown";
		}
	}
	
	private static String pathAlgorithmToString(PathBuilder pathBuilder) {
		switch (pathBuilder) {
			case ContextSensitive:
				return "CONTEXTSENSITIVE";
			case ContextInsensitive :
				return "CONTEXTINSENSITIVE";
			case ContextInsensitiveSourceFinder :
				return "SOURCESONLY";
			default :
				return "UNKNOWN";
		}
	}
	
	
	private static AnalysisResults runAnalysis(final String fileName, final String androidJar) {
		try {

			final SetupApplication	app = new SetupApplication(androidJar, fileName);

			app.setStopAfterFirstFlow(stopAfterFirstFlow);
			app.setEnableImplicitFlows(implicitFlows);
			app.setEnableStaticFieldTracking(staticTracking);
			app.setEnableCallbacks(enableCallbacks);
			app.setEnableExceptionTracking(enableExceptions);
			app.setAccessPathLength(accessPathLength);
			app.setLayoutMatchingMode(layoutMatchingMode);
			app.setFlowSensitiveAliasing(flowSensitiveAliasing);
			app.setPathBuilder(pathBuilder);
			app.setComputeResultPaths(computeResultPaths);
			app.setUseFragments(useFragments);
			app.setInjectionsFilePath(injectionsFilePath);
			final ITaintPropagationWrapper taintWrapper;
	
			//Create a new file from this string paramter
			final EasyTaintWrapper easyTaintWrapper;
			if (easyTaintFilePath != null && new File(easyTaintFilePath).exists())
				easyTaintWrapper = new EasyTaintWrapper(easyTaintFilePath);
			else if (new File("../soot-infoflow/EasyTaintWrapperSource.txt").exists())
				easyTaintWrapper = new EasyTaintWrapper("../soot-infoflow/EasyTaintWrapperSource.txt");
			else
				easyTaintWrapper = new EasyTaintWrapper("EasyTaintWrapperSource.txt");
			easyTaintWrapper.setAggressiveMode(aggressiveTaintWrapper);
			taintWrapper = easyTaintWrapper;
		
			
			app.addPreprocessor(new RewireFlow(injectionsFilePath));
			
			
			app.setTaintWrapper(taintWrapper);
			app.calculateSourcesSinksEntrypoints(sourcesAndSinksFilePath);
			//app.calculateSourcesSinksEntrypoints("SuSiExport.xml");
			
			
			if (DEBUG) {
				app.printEntrypoints();
				app.printSinks();
				app.printSources();
				
				app.addPreprocessor(new DebugHelp());
			}
				
			System.out.println("Running data flow analysis...");
			final InfoflowResults res = app.runInfoflow();
			
			AnalysisResults r = new AnalysisResults();
			r.context = app;
			r.infoFlowResults = res;
			return r;
			
		} catch (IOException ex) {
			System.err.println("Could not read file: " + ex.getMessage());
			ex.printStackTrace();
			throw new RuntimeException(ex);
		} catch (XmlPullParserException ex) {
			System.err.println("Could not read Android manifest file: " + ex.getMessage());
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
	}
	

//TODO need a proper parsing library for this
	private static void printUsage() {
		System.out.println("FlowDroid (c) Secure Software Engineering Group @ EC SPRIDE");
		System.out.println();
		System.out.println("Incorrect arguments: [0] = apk-file, [1] = android-jar-directory");
		System.out.println("Optional further parameters:");
		System.out.println("\t--TIMEOUT n Time out after n seconds");
		System.out.println("\t--SYSTIMEOUT n Hard time out (kill process) after n seconds, Unix only");
		System.out.println("\t--SINGLEFLOW Stop after finding first leak");
		System.out.println("\t--IMPLICIT Enable implicit flows");
		System.out.println("\t--NOSTATIC Disable static field tracking");
		System.out.println("\t--NOEXCEPTIONS Disable exception tracking");
		System.out.println("\t--APLENGTH n Set access path length to n");
		System.out.println("\t--CGALGO x Use callgraph algorithm x");
		System.out.println("\t--NOCALLBACKS Disable callback analysis");
		System.out.println("\t--LAYOUTMODE x Set UI control analysis mode to x");
		System.out.println("\t--ALIASFLOWINS Use a flow insensitive alias search");
		System.out.println("\t--NOPATHS Do not compute result paths");
		System.out.println("\t--AGGRESSIVETW Use taint wrapper in aggressive mode");
		System.out.println("\t--PATHALGO Use path reconstruction algorithm x");
		System.out.println("\t--FRAGMENTS Enable use of Fragments, not enabled by default");
		System.out.println("\t--SOURCESSINKS Full path of SourcesAndSinks.txt");
		System.out.println("\t--EASYTAINT Full path of easy taint wrapper file.");
		System.out.println();
		System.out.println("Supported callgraph algorithms: AUTO, CHA, RTA, VTA, SPARK");
		System.out.println("Supported layout mode algorithms: NONE, PWD, ALL");
		System.out.println("Supported path algorithms: CONTEXTSENSITIVE, CONTEXTINSENSITIVE, SOURCESONLY");
	}

	private static final class AnalysisResults {
		InfoflowResults infoFlowResults;
		SetupApplication context;
	}
	
	
	
}
