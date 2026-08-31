
import json
import pandas as pd
import matplotlib.pyplot as plt


# ============================================================
# 1. Configuration
# ============================================================

file_path = "rq1_results.json"
output_chart = "failure_rate_chart.png"

# Toxiproxy latency injection window
# Change these values if your injection happened at different times.
LATENCY_START = 5
LATENCY_END = 15


# ============================================================
# 2. Read and parse the NDJSON file
# ============================================================

data = []

with open(file_path, "r", encoding="utf-8") as f:

    for line_number, line in enumerate(f, start=1):

        line = line.strip()

        # Skip empty lines
        if not line:
            continue

        # Parse JSON
        try:
            obj = json.loads(line)
        except json.JSONDecodeError as e:
            print(f"⚠️ Skipping invalid JSON on line {line_number}: {e}")
            continue

        # Get metric name safely
        metric_name = obj.get("metric")

        # Only process these two metrics
        if metric_name not in ["http_reqs", "http_req_failed"]:
            continue

        # Get data safely
        record_data = obj.get("data", {})

        if not isinstance(record_data, dict):
            print(f"⚠️ Skipping line {line_number}: data is not an object")
            continue

        # Get timestamp
        ts = record_data.get("time")

        # Get value
        value = record_data.get("value")

        # Skip records missing time or value
        if ts is None:
            print(f"⚠️ Skipping line {line_number}: missing data.time")
            continue

        if value is None:
            print(f"⚠️ Skipping line {line_number}: missing data.value")
            continue

        # Convert timestamp
        try:
            dt = pd.to_datetime(ts)
        except Exception as e:
            print(f"⚠️ Skipping line {line_number}: invalid timestamp {ts}")
            continue

        # Floor timestamp to nearest second
        second_bucket = dt.floor("s")

        data.append({
            "time": second_bucket,
            "metric": metric_name,
            "value": value
        })


# ============================================================
# 3. Check whether data was found
# ============================================================

if not data:
    print("❌ No http_reqs or http_req_failed data was found.")
    print("Check that rq1_results.json contains k6 metric data.")
    exit()


print(f"✅ Successfully parsed {len(data)} records")


# ============================================================
# 4. Convert to DataFrame
# ============================================================

df = pd.DataFrame(data)

print("\nMetrics found:")
print(df["metric"].value_counts())


# ============================================================
# 5. Group data into 1-second buckets
# ============================================================

grouped = (
    df.groupby(["time", "metric"])["value"]
    .sum()
    .unstack(fill_value=0)
)


# ============================================================
# 6. Make sure both columns exist
# ============================================================

if "http_reqs" not in grouped.columns:
    grouped["http_reqs"] = 0

if "http_req_failed" not in grouped.columns:
    grouped["http_req_failed"] = 0


# ============================================================
# 7. Calculate failure rate
# ============================================================

# Avoid division by zero
grouped["failure_rate_pct"] = (
    grouped["http_req_failed"]
    / grouped["http_reqs"].replace(0, 1)
) * 100

# If there were zero requests, failure rate should be 0
grouped.loc[
    grouped["http_reqs"] == 0,
    "failure_rate_pct"
] = 0.0


# ============================================================
# 8. Convert timestamps to elapsed seconds
# ============================================================

start_time = grouped.index.min()

elapsed_seconds = (
    grouped.index - start_time
).total_seconds()


# ============================================================
# 9. Print summary
# ============================================================

print("\n========================================")
print("K6 FAILURE RATE SUMMARY")
print("========================================")

print(f"Start time       : {start_time}")
print(f"End time         : {grouped.index.max()}")
print(f"Duration         : {elapsed_seconds.max():.0f} seconds")

print(
    f"Total HTTP reqs  : "
    f"{grouped['http_reqs'].sum():.0f}"
)

print(
    f"Failed requests  : "
    f"{grouped['http_req_failed'].sum():.0f}"
)

total_requests = grouped["http_reqs"].sum()
total_failures = grouped["http_req_failed"].sum()

if total_requests > 0:
    overall_failure_rate = (
        total_failures / total_requests
    ) * 100
else:
    overall_failure_rate = 0

print(
    f"Overall failure rate : "
    f"{overall_failure_rate:.2f}%"
)

print("========================================\n")


# ============================================================
# 10. Create chart
# ============================================================

plt.figure(figsize=(14, 7))


# Plot failure rate
plt.plot(
    elapsed_seconds,
    grouped["failure_rate_pct"].values,
    marker="o",
    label="Failure Rate (%)",
    linewidth=2
)


# ============================================================
# 11. Highlight Toxiproxy latency injection
# ============================================================

plt.axvspan(
    LATENCY_START,
    LATENCY_END,
    alpha=0.3,
    label=(
        f"Toxiproxy Latency Injection "
        f"({LATENCY_START}s - {LATENCY_END}s)"
    )
)


# ============================================================
# 12. Chart formatting
# ============================================================

plt.title(
    "HTTP Request Failure Rate per Second (k6 Load Test)",
    fontsize=14,
    fontweight="bold"
)

plt.xlabel(
    "Time (Seconds Elapsed)",
    fontsize=12
)

plt.ylabel(
    "Failure Rate (%)",
    fontsize=12
)

plt.ylim(0, 105)

plt.legend(loc="upper right")

plt.grid(
    True,
    linestyle="--",
    alpha=0.7
)


# ============================================================
# 13. X-axis formatting
# ============================================================

max_seconds = int(elapsed_seconds.max())

plt.xticks(
    range(
        0,
        max_seconds + 2,
        2
    )
)


# ============================================================
# 14. Save chart
# ============================================================

plt.tight_layout()

plt.savefig(
    output_chart,
    dpi=300,
    bbox_inches="tight"
)

print(f"✅ Chart saved as: {output_chart}")


# ============================================================
# 15. Display chart
# ============================================================

plt.show()
