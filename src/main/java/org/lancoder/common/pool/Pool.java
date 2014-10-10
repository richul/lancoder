package org.lancoder.common.pool;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.lancoder.common.Service;

public abstract class Pool<T> extends Service implements PoolListener<T> {

	/**
	 * How many poolers can be initialized in the pool
	 */
	private int threadLimit;
	/**
	 * The pool will accept tasks and send to a queue if no pooler can be used. Otherwise,
	 */
	private boolean canQueue;
	/**
	 * List of the initialized poolers in the pool
	 */
	protected final ArrayList<Pooler<T>> poolers = new ArrayList<>();
	/**
	 * Contains the tasks to send to poolers
	 */
	protected final LinkedBlockingQueue<T> todo = new LinkedBlockingQueue<>();
	/**
	 * Thread group of the poolers
	 */
	protected final ThreadGroup threads = new ThreadGroup("threads");
	/**
	 * The listener of the pool
	 */
	protected final PoolListener<T> listener;

	public Pool(int threadLimit, PoolListener<T> listener) {
		this(threadLimit, listener, true);
	}

	public Pool(int threadLimit, PoolListener<T> listener, boolean canQueue) {
		this.threadLimit = threadLimit;
		this.listener = listener;
		this.canQueue = canQueue;
	}

	/**
	 * Count the number of currently busy pooler resource
	 * 
	 * @return The busy pooler count
	 */
	public int getActiveCount() {
		int count = 0;
		for (Pooler<T> pooler : this.poolers) {
			if (pooler.isActive()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Public synchronized call to get hasFree
	 * 
	 * @return hasFree()
	 */
	public synchronized boolean hasFreeConverters() {
		return hasFree();
	}

	private Pooler<T> spawn(Pooler<T> pooler) {
		new Thread(threads, pooler).start();
		return pooler;
	}

	protected abstract Pooler<T> getNewPooler();

	/**
	 * Get a free pooler resource or create a new one.
	 * 
	 * @return A free pooler or null if no pooler are available.
	 */
	private Pooler<T> getFreePooler() {
		Pooler<T> pooler = null;
		for (Pooler<T> p : poolers) {
			if (!p.isActive()) {
				pooler = p;
			}
		}
		return pooler == null && hasFree() ? spawn(getNewPooler()) : pooler;
	}

	protected boolean hasFree() {
		return poolers.size() < threadLimit;
	}

	/**
	 * Try to add an item to the pool. If pool is not allowed to have a queue and all poolers are busy, return false.
	 * 
	 * @param element
	 *            The element to handle
	 * @return If element could be added to queue
	 */
	public boolean handle(T element) {
		boolean handled = false;
		if (canQueue || (todo.size() == 0 && hasFree())) {
			this.todo.add(element);
			refresh();
			handled = true;
		}
		return handled;
	}

	/**
	 * Try to give todo items to poolers.
	 */
	protected void refresh() {
		boolean caughtNull = false;
		while (todo.size() > 0 && hasFree() && !caughtNull) {
			Pooler<T> pooler = this.getFreePooler();
			if (pooler != null) {
				pooler.add(todo.poll());
			} else {
				caughtNull = true;
			}
		}
	}

	public synchronized int getThreadLimit() {
		return threadLimit;
	}

	@Override
	public void stop() {
		super.stop();
		for (Pooler<T> converter : poolers) {
			converter.stop();
		}
		threads.interrupt();
	}

	public void started(T e) {
		listener.started(e);
	}

	public void completed(T e) {
		listener.completed(e);
		refresh();
	}

	public void failed(T e) {
		listener.failed(e);
		refresh();
	}

	public void crash(Exception e) {
		// TODO
		e.printStackTrace();
	}
}
