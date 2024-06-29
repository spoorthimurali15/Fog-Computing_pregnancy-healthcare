package org.fog.test.perfeval;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
public class Health_topo1 {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static int numOfAreas = 4 ;//fog nodes
	
	static int numOfECGPerArea=1;
	static int numOfThermoPerArea=1;
	static int numOfSphygmoPerArea=1;

	static double READING_TIME = 5;
	private static boolean CLOUD = false;
	public static void main(String[] args) {
		Log.printLine("Starting smart healthcare system...");
		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			String appId = "healthcare"; // identifier of the application
			FogBroker broker = new FogBroker("broker");
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			createFogDevices(broker.getId(), appId);
			Controller controller = null;
			
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("h")){ // names of all Smart ecgs start with 'm' 
					moduleMapping.addModuleToDevice("heart-rate", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart ecg
				}
			}
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("b")){ // names of all Smart ecgs start with 'm' 
					moduleMapping.addModuleToDevice("blood-pressure", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart ecg
				}
			}
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("t")){ // names of all Smart ecgs start with 'm' 
					moduleMapping.addModuleToDevice("temperature", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart ecg
				}
			}
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("a")){ // names of all fog devices start with 'a' 
					moduleMapping.addModuleToDevice("slot-detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart ecg
				}
			}
			//moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("heart-rate", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("blood-pressure", "cloud");
				moduleMapping.addModuleToDevice("temperature", "cloud");
				moduleMapping.addModuleToDevice("slot-detector", "cloud"); // placing all instances of Object Tracker module in the Cloud
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("Pregnancy Healthcare system simulation finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<numOfAreas;i++){
			addArea(i+"", userId, appId, proxy.getId());
		}
	}

	private static FogDevice addArea(String id, int userId, String appId, int parentId){
		FogDevice router = createFogDevice("area-"+id, 2800, 4000, 1000, 10000, 2, 0.0, 107.339, 83.4333);
		fogDevices.add(router);
		router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
		for(int i=0;i<numOfECGPerArea;i++){
			String mobileId = id+"-"+i;
			FogDevice ecg = addECG(mobileId, userId, appId, router.getId()); // adding a smart ecg to the physical topology. Smart ecgs have been modeled as fog devices as well.
			ecg.setUplinkLatency(2); // latency of connection between ecg and router is 2 ms
			fogDevices.add(ecg);
		}
		for(int i=0;i<numOfSphygmoPerArea;i++){
			String mobileId = id+"-"+i;
			FogDevice bp = addSphygmo(mobileId, userId, appId, router.getId()); // adding a smart ecg to the physical topology. Smart ecgs have been modeled as fog devices as well.
			bp.setUplinkLatency(2); // latency of connection between ecg and router is 2 ms
			fogDevices.add(bp);
		}
		for(int i=0;i<numOfThermoPerArea;i++){
			String mobileId = id+"-"+i;
			FogDevice th = addThermo(mobileId, userId, appId, router.getId()); // adding a smart ecg to the physical topology. Smart ecgs have been modeled as fog devices as well.
			th.setUplinkLatency(2); // latency of connection between ecg and router is 2 ms
			fogDevices.add(th);
		}
		router.setParentId(parentId);
		return router;
	}
	
	private static FogDevice addECG(String id, int userId, String appId, int parentId){
		FogDevice ecg = createFogDevice("hr-"+id, 100, 512, 10000, 5000, 3, 0, 0.1, 0.5);
		ecg.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, "ecg", userId, appId, new DeterministicDistribution(READING_TIME)); // inter-transmission time of ecg (sensor) follows a deterministic distribution
		sensors.add(sensor);
		Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
		actuators.add(ptz);
		sensor.setGatewayDeviceId(ecg.getId());  
		sensor.setLatency(40.0);  // latency of connection between ecg (sensor) and the parent Smart ecg is 1 ms
		ptz.setGatewayDeviceId(parentId);
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart ecg is 1 ms
		return ecg;
	}
	private static FogDevice addSphygmo(String id, int userId, String appId, int parentId){
		FogDevice bp = createFogDevice("bp-"+id, 50, 256, 5000, 2000, 3, 0.1, 0.5, 0.2);
		bp.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, "bp", userId, appId, new DeterministicDistribution(READING_TIME)); // inter-transmission time of ecg (sensor) follows a deterministic distribution
		sensors.add(sensor);
		Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
		actuators.add(ptz);
		sensor.setGatewayDeviceId(bp.getId());  
		sensor.setLatency(40.0);  // latency of connection between ecg (sensor) and the parent Smart ecg is 1 ms
		ptz.setGatewayDeviceId(parentId);
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart ecg is 1 ms
		return bp;
	}
	private static FogDevice addThermo(String id, int userId, String appId, int parentId){
		FogDevice th = createFogDevice("temp-"+id, 25, 128, 1000, 1000, 3, 0.1, 0.2, 0.1);
		th.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, "th", userId, appId, new DeterministicDistribution(READING_TIME)); // inter-transmission time of ecg (sensor) follows a deterministic distribution
		sensors.add(sensor);
		Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
		actuators.add(ptz);
		sensor.setGatewayDeviceId(th.getId());  
		sensor.setLatency(40.0);  // latency of connection between ecg (sensor) and the parent Smart ecg is 1 ms
		ptz.setGatewayDeviceId(parentId);
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart ecg is 1 ms
		return th;
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);
		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);
		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the Intelligent Surveillance application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("heart-rate", 10);
		application.addAppModule("blood-pressure", 10);
		application.addAppModule("temperature", 10);
		application.addAppModule("slot-detector", 10);
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		application.addAppEdge("ecg", "heart-rate", 1000, 500, "ecg", Tuple.UP, AppEdge.SENSOR); // adding edge from ecg (sensor) to Motion Detector module carrying tuples of type ecg
		application.addAppEdge("bp", "blood-pressure", 1000, 500, "bp", Tuple.UP, AppEdge.SENSOR); // adding edge from ecg (sensor) to Motion Detector module carrying tuples of type ecg
		application.addAppEdge("th", "temperature", 1000, 500, "th", Tuple.UP, AppEdge.SENSOR); // adding edge from ecg (sensor) to Motion Detector module carrying tuples of type ecg

		application.addAppEdge("heart-rate", "slot-detector",
				1000, 500, "slots",Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("blood-pressure", "slot-detector",
				1000, 500, "slots",Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("temperature", "slot-detector",
				1000, 500, "slots",Tuple.UP, AppEdge.MODULE);
				// adding edge from Slot Detector to PTZ CONTROL (actuator)
				application.addAppEdge("slot-detector", "PTZ_CONTROL", 100,
				28, 100, "PTZ_PARAMS",
				Tuple.UP, AppEdge.ACTUATOR);
				application.addTupleMapping("heart-rate", "ecg", "slots",
				new FractionalSelectivity(1.0));
				application.addTupleMapping("blood-pressure", "bp", "slots",
						new FractionalSelectivity(1.0));
				application.addTupleMapping("temperature", "th", "slots",
						new FractionalSelectivity(1.0));
				application.addTupleMapping("slot-detector", "slots",
				"PTZ_PARAMS", new FractionalSelectivity(1.0));
				final AppLoop loop1 = new AppLoop(new ArrayList<String>()
				{{add("ecg");
				add("heart-rate");add("blood-pressure");
				add("tempearture");add("slot-detector");
				add("PTZ_CONTROL");}});
				List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
				application.setLoops(loops);
				return application;
				}
	}
