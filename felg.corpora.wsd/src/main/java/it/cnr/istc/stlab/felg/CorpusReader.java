package it.cnr.istc.stlab.felg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import getalp.wsd.ufsac.utils.CorpusLemmatizer;
import it.cnr.istc.stlab.lgu.commons.files.FileUtils;

public class CorpusReader {

	private static final Logger logger = LogManager.getLogger(CorpusReader.class);

	public static void main(String[] args) throws CompressorException, IOException {
		logger.info("Running FELG - Corpus Reader");

		try {
			Configurations configs = new Configurations();
			Configuration config;
			if (args.length > 0) {
				config = configs.properties(args[0]);
			} else {
				config = configs.properties("config.properties");
			}

			Properties props = new Properties();
			props.setProperty("annotators", "tokenize, ssplit, pos");
			StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

			CorpusLemmatizer lemmatizer = new CorpusLemmatizer();

			String outputFolder = config.getString("outputFolder");
			String python_path = config.getString("python_path");
			String data_path = config.getString("data_path");
			int concurent_threads = config.getInt("concurrent_threads");
			int batch_size = config.getInt("batch_size");
			boolean useOnlyAbstract = config.getBoolean("useOnlyAbstract");
			boolean excludeWrite = config.getBoolean("excludeWrite");
			boolean useCompression = config.getBoolean("useCompression");

			logger.info(ConfigurationUtils.toString(config));

			AtomicLong count = new AtomicLong();

			// initialize wsd
//			NeuralWSDDecode[] nwds = new NeuralWSDDecode[concurent_threads];

			NeuralDisambiguatorReader ndr = new NeuralDisambiguatorReader(python_path, data_path,
					Lists.newArrayList(config.getString("weights")));

			ReaderWorker rw = new ReaderWorker(null, ndr, null, null, null, null, 0, false, false, false);
			rw.readFile("/tf/out/AA/wiki_08_d.bz2", "/tf/out/AA/wiki_08_dd.bz2");

//
//			// splitting input
//			List<String> filepaths = FileUtils.getFilesUnderTreeRec(config.getString("wikiFolder"));
//			List<List<String>> listsToProcess = new ArrayList<>();
//			int chunkSize = filepaths.size() / concurent_threads;
//			for (int i = 0; i < filepaths.size(); i += chunkSize) {
//				listsToProcess.add(filepaths.subList(i, Math.min(i + chunkSize, filepaths.size())));
//				logger.trace(String.format("from %s to %s", i, Math.min(i + chunkSize, filepaths.size())));
//			}
//
//			ExecutorService executor = Executors.newFixedThreadPool(concurent_threads);
//			long t0 = System.currentTimeMillis();
//			for (int i = 0; i < concurent_threads; i++) {
//				executor.execute(new WSDWorker(listsToProcess.get(i), nwds[i], outputFolder, count, pipeline,
//						lemmatizer, t0, useOnlyAbstract, excludeWrite, useCompression));
//			}
//			executor.shutdown();
//			executor.awaitTermination(10, TimeUnit.DAYS);
//			logger.info("Number of articles processed " + count.get());
//			for (int i = 0; i < concurent_threads; i++) {
//				nwds[i].close();
//				logger.info("Closing WSD");
//			}
//			executor.shutdownNow();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
