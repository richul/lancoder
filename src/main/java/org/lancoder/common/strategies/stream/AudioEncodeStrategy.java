package org.lancoder.common.strategies.stream;

import java.util.ArrayList;

import org.lancoder.common.codecs.ChannelDisposition;
import org.lancoder.common.codecs.base.AudioCodec;
import org.lancoder.common.file_components.streams.AudioStream;
import org.lancoder.common.job.Job;
import org.lancoder.common.job.RateControlType;
import org.lancoder.common.task.ClientTask;
import org.lancoder.common.task.StreamConfig;
import org.lancoder.common.task.Unit;
import org.lancoder.common.task.audio.AudioStreamConfig;
import org.lancoder.common.task.audio.AudioTask;
import org.lancoder.common.task.audio.ClientAudioTask;

public class AudioEncodeStrategy extends EncodeStrategy {

	private static final long serialVersionUID = -62089041693461179L;
	private ChannelDisposition channels = ChannelDisposition.ORIGINAL;
	private int sampleRate;

	public AudioEncodeStrategy(AudioCodec codec, RateControlType rateControlType, int rate,
			ChannelDisposition channels, int sampleRate) {
		super(codec, rateControlType, rate);
		this.channels = channels;
		this.sampleRate = sampleRate;
	}

	public ChannelDisposition getChannels() {
		return channels;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	@Override
	public ArrayList<ClientTask> createTasks(Job job, StreamConfig config) {
		AudioStream outStream = (AudioStream) config.getOutStream();
		ArrayList<ClientTask> tasks = new ArrayList<>();

		int taskId = job.getTaskCount();

		AudioTask task = new AudioTask(taskId, job.getJobId(), 0, outStream.getUnitCount(), outStream.getUnitCount(),
				Unit.SECONDS, getRelativeTaskTempFile(job, taskId), getRelativeTaskFinalFile(job, taskId));

		tasks.add(new ClientAudioTask(task, (AudioStreamConfig) config));

		return tasks;
	}

}
