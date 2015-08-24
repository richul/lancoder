package org.lancoder.common.strategies.stream;

import java.io.File;
import java.util.ArrayList;

import org.lancoder.common.codecs.base.AbstractCodec;
import org.lancoder.common.job.Job;
import org.lancoder.common.job.RateControlType;
import org.lancoder.common.utils.FileUtils;

public abstract class EncodeStrategy extends StreamHandlingStrategy {

	private static final long serialVersionUID = -7078509107858295060L;
	protected AbstractCodec codec;
	protected RateControlType rateControlType;
	protected int rate;

	public EncodeStrategy(AbstractCodec codec, RateControlType rateControlType, int rate) {
		this.codec = codec;
		this.rateControlType = rateControlType;
		this.rate = rate;
	}

	@Override
	public ArrayList<String> getRateControlArgs() {
		ArrayList<String> args = new ArrayList<String>();
		if (codec.isLossless()) {
			return args;
		}
		String rateControlArg = rateControlType == RateControlType.VBR ? codec.getVBRSwitchArg() : codec
				.getCRFSwitchArg();
		String rateArg = rateControlType == RateControlType.VBR ? codec.formatBitrate(this.rate) : codec
				.formatQuality(this.rate);
		args.add(rateControlArg);
		args.add(rateArg);
		return args;
	}

	protected File getRelativeTaskFinalFile(Job job, int taskId) {
		String s = job.getOutputFolder();
		return FileUtils.getFile(s, "parts", String.valueOf(taskId),
				String.format("part-%d.%s", taskId, getCodec().getContainer()));
	}

	@Override
	public AbstractCodec getCodec() {
		return codec;
	}

	public RateControlType getRateControlType() {
		return rateControlType;
	}

	public int getRate() {
		return rate;
	}

}
