package org.lancoder.worker.converter.video;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lancoder.common.FilePathManager;
import org.lancoder.common.exceptions.MissingDecoderException;
import org.lancoder.common.exceptions.MissingThirdPartyException;
import org.lancoder.common.file_components.streams.VideoStream;
import org.lancoder.common.file_components.streams.original.OriginalVideoStream;
import org.lancoder.common.task.video.ClientVideoTask;
import org.lancoder.common.third_parties.FFmpeg;
import org.lancoder.common.utils.FileUtils;
import org.lancoder.common.utils.TimeUtils;
import org.lancoder.ffmpeg.FFmpegReader;
import org.lancoder.worker.converter.Converter;
import org.lancoder.worker.converter.ConverterListener;

public class VideoWorkThread extends Converter<ClientVideoTask> {

	private static String OS = System.getProperty("os.name").toLowerCase();

	private static Pattern currentFramePattern = Pattern.compile("frame=\\s+([0-9]+)");
	private static Pattern missingDecoder = Pattern.compile("Error while opening encoder for output stream");

	private FFmpegReader ffMpegWrapper = new FFmpegReader();
	private Transcoder transcoder = new Transcoder();

	public VideoWorkThread(ConverterListener listener, FilePathManager filePathManager, FFmpeg ffMpeg) {
		super(listener, filePathManager, ffMpeg);
	}

	@Override
	public void stop() {
		super.stop();
		ffMpegWrapper.stop();
		transcoder.stop();
	}

	private static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public boolean encodePass(String startTimeStr, String durationStr) throws MissingDecoderException,
			MissingThirdPartyException {
		OriginalVideoStream inStream = task.getStreamConfig().getOrignalStream();
		VideoStream outStream = task.getStreamConfig().getOutStream();
		String encodingLibrary = outStream.getCodec().getEncoder();
		File inputFile = filePathManager.getSharedSourceFile(task);
		String mapping = String.format("0:%d", inStream.getIndex());
		// Get parameters from the task and bind parameters to process
		String[] baseArgs = new String[] { ffMpeg.getPath(), "-ss", startTimeStr, "-t", durationStr, "-i",
				inputFile.getAbsolutePath(), "-sn", "-force_key_frames", "0", "-an", "-map", mapping, "-c:v",
				encodingLibrary };
		ArrayList<String> ffmpegArgs = new ArrayList<>();
		// Add base args to process builder
		Collections.addAll(ffmpegArgs, baseArgs);

		ffmpegArgs.addAll(outStream.getRateControlArgs());

		// output file and pass arguments
		File outFile = filePathManager.getLocalTempFile(task);
		String outFileStr = outFile.getAbsolutePath();

		if (task.getStepCount() > 1) {
			// Add pass arguments
			ffmpegArgs.add("-pass");
			ffmpegArgs.add(String.valueOf(task.getProgress().getCurrentStepIndex()));
			if (task.getProgress().getCurrentStepIndex() != task.getStepCount()) {
				ffmpegArgs.add("-f");
				ffmpegArgs.add("rawvideo");
				ffmpegArgs.add("-y");
				// Change output file to null
				outFileStr = isWindows() ? "NUL" : "/dev/null";
			}
		}
		ffmpegArgs.add(outFileStr);
		ffMpegWrapper = new FFmpegReader();
		// Start process in task output directory (log and mtrees pass files generated by ffmpeg)
		return ffMpegWrapper.read(ffmpegArgs, this, true, outFile.getParentFile());
	}

	private boolean transcodeToMpegTs() {
		File destination = filePathManager.getSharedFinalFile(task);
		File source = filePathManager.getLocalTempFile(task);

		if (filePathManager.getSharedFinalFile(task).exists()) {
			System.err.printf("Cannot transcode to mkv as file %s already exists\n", destination.getPath());
			return false;
		}
		String[] baseArgs = new String[] { ffMpeg.getPath(), "-i", source.getAbsolutePath(), "-f", "mpegts", "-c",
				"copy", "-bsf:v", "h264_mp4toannexb", destination.getPath() };
		ArrayList<String> args = new ArrayList<>();
		Collections.addAll(args, baseArgs);
		try {
			transcoder = new Transcoder();
			transcoder.read(args);
			FileUtils.givePerms(destination, false);
		} catch (MissingDecoderException e) {
			e.printStackTrace();
		} catch (MissingThirdPartyException e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public void serviceFailure(Exception e) {
		e.printStackTrace();
	}

	@Override
	public void onMessage(String line) {
		Matcher m = currentFramePattern.matcher(line);
		if (m.find()) {
			long units = Long.parseLong(m.group(1));
			task.getProgress().update(units);
		}
		m = missingDecoder.matcher(line);
		if (m.find()) {
			System.err.println("Missing decoder !");
			listener.taskFailed(task);
			// listener.crash(new MissingDecoderException("Missing decoder or encoder"));
		}
	}

	@Override
	protected void start() {
		boolean success = true;
		try {
			listener.taskStarted(task);
			createDirs();
			// use start and duration for ffmpeg legacy support
			long durationMs = task.getEncodingEndTime() - task.getEncodingStartTime();
			String startTimeStr = TimeUtils.getStringFromMs(task.getEncodingStartTime());
			String durationStr = TimeUtils.getStringFromMs(durationMs);

			int currentStep = 1;
			while (currentStep <= task.getStepCount() && success) {
				System.err.printf("Encoding pass %d of %d\n", task.getProgress().getCurrentStepIndex(),
						task.getStepCount());
				success = encodePass(startTimeStr, durationStr);
				if (success) {
					task.getProgress().completeStep();
				}
				currentStep++;
			}
			if (success) {
				// TODO move to a codec strategy
				if (task.getStreamConfig().getOutStream().getCodec().needsTranscode()) {
					success = transcodeToMpegTs();
				} else {
					try {
						File destination = filePathManager.getSharedFinalFile(task);
						File source = filePathManager.getLocalTempFile(task);
						FileUtils.moveFile(source, destination);
						FileUtils.givePerms(destination, false);
					} catch (IOException e) {
						e.printStackTrace();
						success = false;
					}
				}
			}
		} catch (MissingThirdPartyException | MissingDecoderException e) {
			// listener.crash(e);
			e.printStackTrace();
			listener.taskFailed(task);
		} finally {
			if (success) {
				listener.taskCompleted(task);
			} else {
				listener.taskFailed(task);
			}
		}
		this.destroyTempFolder();
	}

	@Override
	public void cancelTask(Object task) {
		if (this.task != null && this.task.equals(task)) {
			this.ffMpegWrapper.stop();
			if (transcoder != null) {
				this.transcoder.stop();
			}
		}
	}
}
