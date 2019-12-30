package testing.saker.nest;

import java.lang.ref.WeakReference;

import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.nest.NestMetric;

public class TestFlag {
	private static final NestMetric NULL_METRIC_INSTANCE = new NestMetric() {
	};
	public static final boolean ENABLED = true;
	private static final InheritableThreadLocal<WeakReference<NestMetric>> METRIC_THREADLOCAL = new InheritableThreadLocal<>();

	private TestFlag() {
		throw new UnsupportedOperationException();
	}

	public static void set(NestMetric metric) {
		if (metric == null) {
			METRIC_THREADLOCAL.remove();
		} else {
			METRIC_THREADLOCAL.set(new WeakReference<>(metric));
		}
	}

	public static NestMetric metric() {
		NestMetric metric = ObjectUtils.getReference(METRIC_THREADLOCAL.get());
		if (metric != null) {
			return metric;
		}
		return NULL_METRIC_INSTANCE;
	}
}
