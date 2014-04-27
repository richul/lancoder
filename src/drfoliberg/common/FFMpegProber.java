package drfoliberg.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FFMpegProber {

	/**
	 * Get the duration in second of a media file
	 * 
	 * @param filename
	 *            The file to parse
	 * @return The number of seconds or -1 if not found
	 */
	public static float getSecondsDuration(String filename) {
		Process process = null;
		boolean found = false;
		try {
			process = Runtime.getRuntime().exec("avprobe " + filename);
		} catch (IOException e) {
			return -1;
		}

		// Read from ffmpeg stderr to get progress
		InputStream stderr = process.getErrorStream();
		Scanner s = new Scanner(stderr);
		String line = "";

		// Duration: 01:24:20.51,
		Pattern durationPattern = Pattern.compile("Duration:"
				+ "\\s*([0-9]{2}):([0-9]{2}):([0-9]{2}\\.[0-9]{2})");

		float totalSeconds = -1;

		while (s.hasNext() && !found) {
			line = s.nextLine();
			Matcher m = durationPattern.matcher(line);
			if (m.find()) {
				found = true;
				int hours = Integer.parseInt(m.group(1));
				int minutes = Integer.parseInt(m.group(2));
				float seconds = Float.parseFloat(m.group(3));
				totalSeconds = hours * 3600;
				totalSeconds += minutes * 60;
				totalSeconds += seconds;
			}
		}
		System.out.println("Scanner closed");
		s.close();
		return totalSeconds;
	}

	/**
	 * Gets frame rate of (first) video stream
	 * 
	 * @param filename
	 *            The filename to parse
	 * @return The frame per second rate or -1 if not found
	 */
	public static float getFrameRate(String filename) {
		boolean found = false;
		Process process = null;
		try {
			process = Runtime.getRuntime().exec("avprobe " + filename);
		} catch (IOException e) {
			return -1;
		}

		// Read from ffmpeg stderr to get progress
		InputStream stderr = process.getErrorStream();
		Scanner s = new Scanner(stderr);
		String line = "";

		// 23.98 fps
		// 25 fps
		Pattern fpsPattern = Pattern.compile("([0-9]+\\.?[0-9]+)\\s*fps");

		float fps = -1;

		while (s.hasNext() && !found) {
			line = s.nextLine();
			Matcher m = fpsPattern.matcher(line);
			if (m.find()) {
				fps = Float.parseFloat(m.group(1));
				found = true;
			}
		}
		System.out.println("Scanner closed");
		s.close();
		return fps;
	}
}