package org.lancoder.common.task.video;

import java.io.File;

import org.lancoder.common.task.Task;
import org.lancoder.common.task.Unit;

public class VideoTask extends Task {

	private static final long serialVersionUID = 3834075993276994157L;

	public VideoTask(int taskId, String jobId, int stepCount, long encodingStartTime, long encodingEndTime,
			long unitCount, Unit unit, File tempFile, File finalFile) {
		super(taskId, jobId, stepCount, encodingStartTime, encodingEndTime, unitCount, unit, tempFile, finalFile);
	}
}
