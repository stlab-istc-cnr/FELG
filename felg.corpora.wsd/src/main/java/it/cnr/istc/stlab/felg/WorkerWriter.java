package it.cnr.istc.stlab.felg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import getalp.wsd.ufsac.core.Sentence;
import getalp.wsd.ufsac.core.Word;
import getalp.wsd.ufsac.utils.CorpusLemmatizer;

public class WorkerWriter implements Runnable {

	private List<String> filepaths;
	private String outputFolder;
	private static final Logger logger = LogManager.getLogger(WorkerWriter.class);
	public static final int SENTENCE_THRESHOLD = 150;
	private AtomicLong count;
	private final long t0;
	private StanfordCoreNLP pipeline;
	private CorpusLemmatizer lemmatizer;
	private NeuralDisambiguatorWriter nwd;
	private boolean useOnlyAbstract, compressOutput = false;

	public WorkerWriter(List<String> filepaths, NeuralDisambiguatorWriter nwd, String outputFolder, AtomicLong count,
			StanfordCoreNLP pipeline, CorpusLemmatizer lemmatizer, long t0, boolean useOnlyAbstract,
			boolean compressOutput) {
		super();
		this.filepaths = filepaths;
		this.outputFolder = outputFolder;
		this.nwd = nwd;
		this.count = count;
		this.pipeline = pipeline;
		this.lemmatizer = lemmatizer;
		this.t0 = t0;
		this.useOnlyAbstract = useOnlyAbstract;
		this.compressOutput = compressOutput;
	}

	@Override
	public void run() {
		long t0article = 0;
		long t1article = 0;
		long timePerArticle = 0;
		long elapsed = 0;
		try {

			for (String filepath : filepaths) {
				logger.trace("Processing " + filepath);
				if (!FilenameUtils.getExtension(filepath).equals("bz2")) {
					continue;
				}

				File inFolder = new File(filepath);

				// create folders for files
				OutputStream os;
				new File(outputFolder + "/" + inFolder.getParentFile().getName()).mkdir();
				if (compressOutput) {
					os = new BZip2CompressorOutputStream(
							new FileOutputStream(new File(outputFolder + "/" + inFolder.getParentFile().getName() + "/"
									+ FilenameUtils.getBaseName(filepath) + ".bz2")));
					logger.trace("Out File: " + outputFolder + "/" + inFolder.getParentFile().getName() + "/"
							+ FilenameUtils.getBaseName(filepath) + ".bz2");
				} else {
					os = new FileOutputStream(new File(outputFolder + "/" + inFolder.getParentFile().getName() + "/"
							+ FilenameUtils.getBaseName(filepath)));
					logger.trace("Out File: " + outputFolder + "/" + inFolder.getParentFile().getName() + "/"
							+ FilenameUtils.getBaseName(filepath));
				}

				ArchiveReader ar = new ArchiveReader(filepath);
				ArticleReader aar;
				while ((aar = ar.nextArticle()) != null) {
					try {
						t0article = System.currentTimeMillis();
						Annotation annotation;
						final String title = aar.getTitle();

						if (useOnlyAbstract) {
							annotation = new Annotation(aar.getAbstract(true));
						} else {
							annotation = new Annotation(aar.getText(true));
						}

						pipeline.annotate(annotation);

						List<Sentence> sentenceBatch = new ArrayList<>();

						annotation.get(SentencesAnnotation.class).forEach(sentence -> {
							List<CoreLabel> t = sentence.get(TokensAnnotation.class);
							CoreLabel[] tokens = t.toArray(new CoreLabel[t.size()]);
							List<Word> words = new ArrayList<>();
							for (int i = 0; i < tokens.length; i++) {
								Word word = new Word(tokens[i].word());
								word.setAnnotation("pos", tokens[i].get(PartOfSpeechAnnotation.class));
								words.add(word);
							}

							if (words.size() > SENTENCE_THRESHOLD) {
								for (int i = 0; i < words.size(); i += SENTENCE_THRESHOLD) {
									if (((i + 1) * SENTENCE_THRESHOLD) < words.size()) {
										Sentence wsdSentence = new Sentence(
												words.subList(i, (i + 1) * SENTENCE_THRESHOLD));
										lemmatizer.tag(wsdSentence.getWords());
										wsdSentence.setAnnotation("art", title);
										sentenceBatch.add(wsdSentence);
									} else {
										Sentence wsdSentence = new Sentence(words.subList(i, words.size()));
										wsdSentence.setAnnotation("art", title);
										lemmatizer.tag(wsdSentence.getWords());
										sentenceBatch.add(wsdSentence);
									}
								}
							} else {
								Sentence wsdSentence = new Sentence(words);
								wsdSentence.setAnnotation("art", title);
								lemmatizer.tag(wsdSentence.getWords());
								sentenceBatch.add(wsdSentence);
							}

						});

						nwd.writeOnOutputStream(sentenceBatch, os);

						t1article = System.currentTimeMillis();
						elapsed = System.currentTimeMillis() - t0;
						timePerArticle = (long) ((double) elapsed / (double) count.incrementAndGet());
						logger.trace("Processed " + aar.getTitle() + " " + timePerArticle + "ms "
								+ (t1article - t0article) + "ms " + sentenceBatch.size() + " ");
					} catch (Exception e) {
						logger.error("Error processing " + aar.getTitle());
						e.printStackTrace();
					}
				}
				os.flush();
				os.close();
			}
			// closing wsd
//			nwd.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

}
