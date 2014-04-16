package drfoliberg.worker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

import drfoliberg.common.Status;
import drfoliberg.common.network.ClusterProtocol;
import drfoliberg.common.network.ConnectMessage;
import drfoliberg.common.network.Message;
import drfoliberg.common.network.StatusReport;
import drfoliberg.common.network.TaskReport;
import drfoliberg.common.task.Task;

public class Worker implements Runnable {

	Config config;
	private Work workThread;
	private Task currentTask;
	private Status status;

	public WorkerServer workerListener;

	public Worker(String name, InetAddress masterIpAddress, int masterPort, int listenPort) {
		this.config = new Config(masterIpAddress, masterPort, listenPort,"",name);
		this.workerListener = new WorkerServer(this);
		print("initialized not connected to a master server");
	}
	
	public Worker(Config config) {
		this.config = config;
	}

	public void shutdown() {
		print("shutting down");
		workerListener.shutdown();
		Socket socket = null;
		try {
			socket = new Socket(config.getMasterIpAddress(), config.getMasterPort());
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
			
			// Send a connect message with a status indicating disconnection
			Message message = new ConnectMessage(config.getUniqueID(), config.getListenPort(), config.getName(), Status.NOT_CONNECTED);
			out.writeObject(message);
			out.flush();
			Object o = in.readObject();
			if (o instanceof Message) {
				Message m = (Message) o;
				switch (m.getCode()) {
				case BYE:
					socket.close();
					break;
				default:
					socket.close();
					print("something odd happened");
					break;
				}
			} else {
				print("received invalid message!");
			}
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		// TODO halt work thread too!
		print("stopping work thread");
		this.workThread.interrupt();
		Thread.currentThread().interrupt();
	}

	public void print(String s) {
		System.out.println((getWorkerName().toUpperCase()) + ": " + s);
	}

	public void taskDone(Task task, InetAddress masterIp) {
		this.getCurrentTask().setProgress(100);
		this.updateStatus(Status.FREE);
	}

	public boolean startWork(Task t) {
		if (this.getStatus() != Status.FREE) {
			print("cannot accept work as i'm not free. Current status: " + this.getStatus());
			return false;
		} else {
			this.currentTask = t;
			this.workThread = new Work(this, t, config.getMasterIpAddress());
			this.workThread.start();
			return true;
		}
	}
	
	/**
	 * Get a status report of the worker.
	 * 
	 * @return the StatusReport object
	 */
	public StatusReport getStatusReport() {
		return new StatusReport(getStatus(), config.getUniqueID(), getTaskReport());
	}

	/**
	 * Get a task report of the current task.
	 * 
	 * @return null if no current task
	 */
	public TaskReport getTaskReport() {
		// if worker has no task, return null report
		TaskReport taskReport = null;
		if (getCurrentTask() != null) {
			taskReport = new TaskReport(config.getUniqueID());
			taskReport.setProgress(getCurrentTask().getProgress());
			taskReport.setJobId(getCurrentTask().getJobId());
			taskReport.setTaskId(getCurrentTask().getTaskId());
		}
		return taskReport;
	}

	public synchronized void updateStatus(Status statusCode) {
		// TODO move this
		print("changing worker status to " + statusCode);
		this.status = statusCode;
		if (statusCode == Status.NOT_CONNECTED) {
			ContactMaster contact = new ContactMaster(this);
			Thread mastercontactThread = new Thread(contact);
			mastercontactThread.start();
		} else {
			// notify the master server of this worker new status
			Socket socket = null;
			try {
				// Init the socket to master
				socket = new Socket(getMasterIpAddress(), config.getMasterPort());
				ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
				out.flush();
				ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
				
				// send report in socket
				out.writeObject(getStatusReport());
				out.flush();
				Object o = in.readObject();
				if (o instanceof Message) {
					Message response = (Message) o;
					if (response.getCode() == ClusterProtocol.BYE) {
						// ?
					} else if (response.getCode() == ClusterProtocol.NEW_UNID) {
						out.writeObject(new Message(ClusterProtocol.BYE, config.getUniqueID()));
						out.flush();
					}
				} else {
					System.out.println("WORKER CONTACT: Could not read what master sent !");
				}
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public int getListenPort() {
		return config.getListenPort();
	}

	public InetAddress getMasterIpAddress() {
		return config.getMasterIpAddress();
	}

	public int getMasterPort() {
		return config.getMasterPort();
	}

	public Status getStatus() {
		return this.status;
	}

	public String getWorkerName() {
		return config.getName();
	}

	public void run() {
		updateStatus(Status.NOT_CONNECTED);
		Thread listerThread = new Thread(workerListener);
		listerThread.start();
	}

	public void setUnid(String unid) {
		print("got id "+ unid + " from master");
		this.config.setUniqueID(unid);
	}

	public Task getCurrentTask() {
		return this.currentTask;
	}
}
