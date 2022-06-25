import simulator.Config;
import simulator.IODevice;
import simulator.Kernel;
import simulator.ProcessControlBlock;
//
import java.io.FileNotFoundException;
import java.io.IOException;
//
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.PriorityQueue;

/**
 * An SJFKernel implements Shortest Job First Scheduling. 
 * 
 * Processes are queued according to the shortest job. Time on the CPU is only relinquished when a new process arrives that is smaller than the one exceuting or blocks for I/O.
 * 
 * @author Zenan Shang credits to: Stephan Jamieson
 * @version 2022-05-16
 */
public class SJFKernel implements Kernel {
    

    /**
     * Queue for processes available for execution ordered by arrival time.
     */
    private PriorityQueue<ProcessControlBlock> readyQueue;

    /**
     * Create an SJFKernel. The kernel does not require any instantiation values, so varargs is ignored. The formal parameter is retained to ensure 
     * uniformity across different kernel types, allowing programs such as Simulate to create kernels on the fly.
     */
    public SJFKernel(Object... varargs) {
        this.readyQueue = new PriorityQueue<ProcessControlBlock>();
    }
    
    /**
     * Place a new process on the CPU. Either the current process has terminated or is now waiting for I/O.
     */
    private ProcessControlBlock dispatch() {
            ProcessControlBlock oldProc;
            if (!readyQueue.isEmpty()) {
                ProcessControlBlock nextProc = readyQueue.remove();
                oldProc = Config.getCPU().contextSwitch(nextProc);
                nextProc.setState(ProcessControlBlock.State.RUNNING);
            }
            else {
                oldProc = Config.getCPU().contextSwitch(null);
            }
            return oldProc;
    }
            
    
    /**
     *  Invoke the system call with the given number (See SystemCall), providing zero or more arguments.
     */                
    public int syscall(int number, Object... varargs) {
        int result = 0;
        switch (number) {
             case MAKE_DEVICE:
                {
                    IODevice device = new IODevice((Integer)varargs[0], (String)varargs[1]);
                    Config.addDevice(device);
                }
                break;
             case EXECVE: 
                {
                    ProcessControlBlock pcb = this.loadProgram((String)varargs[0]);
                    if (pcb!=null) {
                        // Loaded successfully.
                        pcb.setPriority((Integer)varargs[1]);
                        readyQueue.add(pcb);
                        if (Config.getCPU().isIdle()) { 
                           this.dispatch(); 
                        }
                        else 
                        {
                           ProcessControlBlock currentProcess = Config.getCPU().getCurrentProcess();
                           int burstTime_CurrentProcess = currentProcess.getInstruction().getBurstRemaining();
                           
                           ProcessControlBlock comingProcess = readyQueue.peek();
                           int comingProcessBurst = comingProcess.getInstruction().getBurstRemaining();
                           
                           if (comingProcessBurst > burstTime_CurrentProcess) {}
                           else{
                              readyQueue.add(currentProcess);
                              this.dispatch();
                              
                           }
                        }
                    }
                    else {
                        result = -1;
                    }
                }
                break;
             case IO_REQUEST: 
                {
                    ProcessControlBlock ioRequester = Config.getCPU().getCurrentProcess();
                    IODevice device = Config.getDevice((Integer)varargs[0]);
                    device.requestIO((Integer)varargs[1], ioRequester, this);
                    ioRequester.setState(ProcessControlBlock.State.WAITING);
                    dispatch();
                }
                break;
             case TERMINATE_PROCESS:
                {
                    Config.getCPU().getCurrentProcess().setState(ProcessControlBlock.State.TERMINATED);
                    ProcessControlBlock process = dispatch();
                    //process.setState(ProcessControlBlock.State.TERMINATED);
                }
                break;
             default:
                result = -1;
        }
        return result;
    }
   
    
    /**
     * Invoke the interrupt handler (see InterruptHandler), providing the interrupt type and zero or more arguments.
     * The SJF kernel only handles WAKE_UP events, not TIME_OUT events. The latter will cause an IllegalArgumentException to be thrown.
     */
    public void interrupt(int interruptType, Object... varargs){
        switch (interruptType) {
            case TIME_OUT:
                throw new IllegalArgumentException("SJFKernel:interrupt("+interruptType+"...): this kernel does not support timeouts.");
            case WAKE_UP:
                ProcessControlBlock process = (ProcessControlBlock)varargs[1]; 
                process.setState(ProcessControlBlock.State.READY);
                readyQueue.add(process);
                if (Config.getCPU().isIdle()) { 
                   this.dispatch(); 
                } else {
                   ProcessControlBlock currentProcess = Config.getCPU().getCurrentProcess();
                   int currentProcessBurstTime = currentProcess.getInstruction().getBurstRemaining();
                   
                   ProcessControlBlock inComingProcess = readyQueue.peek();
                   int inComingProcessBurstTime  = inComingProcess.getInstruction().getBurstRemaining();
                   if (currentProcess.compareTo(inComingProcess) == 1){
                       readyQueue.add(currentProcess);
                       this.dispatch();
                   }
                }
                break;
            default:
                throw new IllegalArgumentException("SJFKernel:interrupt("+interruptType+"...): unknown type.");
        }
    }
    
    /**
     * Create a ProcessControlBlock for the program with the given file name. 
     * This method is a wrapper for handling I/O exceptions. Its purpose to make the syscall method 'cleaner' and easier to read.
     */
    private static ProcessControlBlock loadProgram(String filename) {
        try {
            return ProcessControlBlock.loadProgram(filename);
        }
        catch (FileNotFoundException fileExp) {
            throw new IllegalArgumentException("SJFKernel: loadProgram(\""+filename+"\"): file not found.");
        }
        catch (IOException ioExp) {
            throw new IllegalArgumentException("SJFKernel: loadProgram(\""+filename+"\"): IO error.");
        }
    }
}