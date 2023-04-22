import sys
import numpy
import matplotlib.pyplot as plt

__INPUT_PATH  = "/Users/tarly127/Desktop/5_1/Dissertação/ApproximateConsensusToolkitMaven/"
__OUTPUT_PATH = "/Users/tarly127/Desktop/5_1/Dissertação/ApproximateConsensusToolkitMaven/output/graphs/"

input_files = {
    "SynchDLPSW86" : {
        4  : "output/output_leader/SynchDLPSW86_12345_3K_all_4.csv",
        6  : "output/output_leader/SynchDLPSW86_12345_3K_all_6.csv",
        8  : "output/output_leader/SynchDLPSW86_12345_3K_all_8.csv",
        16 : "output/output_leader/SynchDLPSW86_12345_3K_all_16.csv",
        32 : "output/output_leader/SynchDLPSW86_12345_3K_all_32.csv",
        64 : "output/output_leader/SynchDLPSW86_12345_3K_all_64.csv",
        128: "output/output_leader/SynchDLPSW86_12345_3K_all_128.csv"
    },
    "FCA" : {
        4  : "output/output_leader/FCA_12345_3K_all _4.csv",
        6  : "output/output_leader/FCA_12345_3K_all _6.csv",
        8  : "output/output_leader/FCA_12345_3K_all _8.csv",
        16 : "output/output_leader/FCA_12345_3K_all _16.csv",
        32 : "output/output_leader/FCA_12345_3K_all _32.csv",
        64 : "output/output_leader/FCA_12345_3K_all _64.csv",
        128: "output/output_leader/FCA_12345_3K_all _128.csv"
    },
    "AsyncDLPSW86" : {
        6  : "output/output_leader/AsynchDLPSW86_12345_3K_all_6.csv",
        8  : "output/output_leader/AsynchDLPSW86_12345_3K_all_8.csv",
        16 : "output/output_leader/AsynchDLPSW86_12345_3K_all_16.csv",
        32 : "output/output_leader/AsynchDLPSW86_12345_3K_all_32.csv",
        64 : "output/output_leader/AsynchDLPSW86_12345_3K_all_64.csv",
        128: "output/output_leader/AsynchDLPSW86_12345_3K_all_128.csv"
    }
}

def get_times(file: str) -> list[float]:
    t_execs = []
    with open(file, "r") as input_file:
        for line in input_file:
            try:
                tokens = line.split(";")
                t_execs.append(float(tokens[0]))
            except ValueError:
                pass

    return t_execs

def graph_times(name: str, values: list[float], title: str = "") -> None:
    plt.clf()
    plt.title(title)
    plt.scatter(range(1, len(values)+1),values, label=name, marker="o")
    plt.ylabel("Texec (ms)")
    plt.xlabel("Execution")
    plt.legend(loc="upper right")
    plt.savefig(f"{__OUTPUT_PATH}{title.strip()}.png")

    #plt.show()

def graph_avg_times(input_values: dict[str, list[float]], title: str = "") -> None:
    plt.clf()
    plt.title(title)

    x_values       = [ 4, 6, 8, 16, 32, 64, 128 ]
    x_values_Async = [ 6, 8, 16, 32, 64, 128 ]

    for name,values in input_values.items():
        plt.scatter(x_values_Async if name == "AsyncDLPSW86" else x_values, values, label=name)
        
        plt.ylabel("Texec (ms)")
        plt.xlabel("#Processes (units)")

    plt.legend(loc="upper right")
    plt.savefig(f"{__OUTPUT_PATH}{title.strip()}.png")

    #plt.show()

def show_exceptions(input: list[float]) -> None:
    mean = numpy.mean(input)
    dev  = numpy.var(input)

    print(f"{mean} : {dev}")

    exceptions = [x for x in input if mean + dev * dev < x or mean - dev * dev > x]

    print(len(exceptions))
    print(exceptions)



def main(args: list[str]) -> None:

    exec_times = {}
    # get t exec for each algorithm
    for alg, files in input_files.items():
        exec_times[alg] =  { n_procs : get_times(__INPUT_PATH + file) for n_procs,file in files.items() }
        
    avgs = {}
    # get the avg runtime for each
    for alg, t_execs in exec_times.items():
        avgs[alg] = [numpy.mean(te) for te in t_execs.values()]
    
    # graph the runtime for each algorithm
    for alg, n_procs_t_exec in exec_times.items():
        for n_procs, t_exec in n_procs_t_exec.items():
            name = f"{alg} ({n_procs} processes)"
            graph_times(name, t_exec, f"Execution time for {name}")

    # graph the average runtime for each algorithm in one graph
    graph_avg_times(avgs, title="Average Execution Time for each Algorithm")


          

if __name__ == "__main__":
    main(sys.argv[1:])
