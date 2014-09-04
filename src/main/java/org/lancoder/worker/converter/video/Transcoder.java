package org.lancoder.worker.converter.video;

import java.util.ArrayList;

import org.lancoder.common.exceptions.MissingDecoderException;
import org.lancoder.common.exceptions.MissingFfmpegException;
import org.lancoder.common.exceptions.WorkInterruptedException;
import org.lancoder.ffmpeg.FFmpegReader;
import org.lancoder.ffmpeg.FFmpegReaderListener;

public class Transcoder extends FFmpegReader implements FFmpegReaderListener {

	public boolean read(ArrayList<String> args) throws WorkInterruptedException, MissingDecoderException,
			MissingFfmpegException {
		return super.read(args, this, true);
	}

	@Override
	public void onMessage(String line) throws MissingDecoderException {
		// TODO would be nice to track progress
	}

}