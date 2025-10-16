import numpy as np
import os
import pandas as pd
import sys

path_to_data_folder = sys.argv[1]

def time_components_to_str(row: pd.Series) -> str:
    return (
        f"{row['st_year']}{str(row['st_month']).zfill(2)}{str(row['st_day']).zfill(2)}"
        f"{str(row['st_hour']).zfill(2)}{str(row['st_minute']).zfill(2)}{str(row['st_second']).zfill(2)}")

for f in sorted(os.listdir(path_to_data_folder)):
    path_to_file = os.path.join(path_to_data_folder, f)
    with open(path_to_file, "r") as f_hdl:
        lines = [line.split(" ") for line in f_hdl.read().split("\n")]
    if lines[-1] == [""]:
        lines = lines[:-1]
    nvalue_index = lines[0].index("nvalue")
    if any([".0" in line[nvalue_index] for line in lines]):
        print(f"{f} contains non-integer value(s) in column 'nvalue'")
    d = pd.read_csv(path_to_file, sep=" ", na_values=["-999.999"])
    d["timestamp"] = d.apply(time_components_to_str , axis=1)
    if any(d["st_minute"] != 0):
        print(f"{f} contains non-zero value(s) in column 'st_minute'")
    if any(np.logical_and(
        d["value"].isna(),
        np.logical_or(
            np.logical_or(np.logical_not(d["value_unc_1"].isna()), np.logical_not(d["value_unc_2"].isna())),
            np.logical_not(d["value_unc_3"].isna()))
        )):
        print(f"{f} contains uncertainty value(s) where no value exists")
    if any(np.logical_and(d["value"].isna(), np.logical_not(d["value_wmo_scale"].isna()))):
        print(f"{f} contains value(s) according to WMO scale where no value exists")
    if any(d["timestamp"].duplicated()):
        print(f"{f} contains duplicated rows")
    for col in d.columns:
        if d[col].dtype == np.float64:
            if any(d[col] == -999.990):
                print(f"{f} contains '-999.990' values in column {col}")
        elif d[col].dtype == np.object_:
            if any(d[col] == "-999.990"):
                print(f"{f} contains '-999.990' values in column {col}")
