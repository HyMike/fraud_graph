"""
Synthetic data generator for fraud_graph (Phase 0, MVP cut).

Emits a small, FIXED synthetic banking graph as CSVs: a background population of
"normal" accounts, plus deliberately planted fraud rings and legitimate look-alike
clusters, each tagged with ground truth so detection queries (Phase 2) and
community detection (Phase 3) can be checked for precision against known answers.

This is intentionally NOT a general-purpose/randomized/adversarial generator.
That's a deferred polish pass (see docs/BUILD_ORDER.md) — this script hand-plants
a fixed, small, reproducible graph so the rest of the MVP has something concrete
to detect against.

No machine learning, no training — this just writes rows to CSV.
"""

import csv
import os
import random
from datetime import datetime, timedelta

random.seed(42)

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "output")

BASE_DATE = datetime(2026, 1, 1)

NORMAL_ACCOUNT_COUNT = 400
NORMAL_TRANSACTION_COUNT = 800

accounts = []       # dict: id, name, opened_at, label, ring_id
devices = []        # dict: id, fingerprint
account_device = []  # dict: account_id, device_id
transactions = []   # dict: id, from_account, to_account, amount, ts

_account_seq = 0
_device_seq = 0
_txn_seq = 0


def next_account_id():
    global _account_seq
    _account_seq += 1
    return f"acct_{_account_seq:04d}"


def next_device_id():
    global _device_seq
    _device_seq += 1
    return f"dev_{_device_seq:04d}"


def next_txn_id():
    global _txn_seq
    _txn_seq += 1
    return f"txn_{_txn_seq:05d}"


def add_account(name, opened_at, label, ring_id=""):
    account_id = next_account_id()
    accounts.append({
        "id": account_id,
        "name": name,
        "opened_at": opened_at.isoformat(),
        "label": label,
        "ring_id": ring_id,
    })
    return account_id


def add_device(fingerprint):
    device_id = next_device_id()
    devices.append({"id": device_id, "fingerprint": fingerprint})
    return device_id


def link_device(account_id, device_id):
    account_device.append({"account_id": account_id, "device_id": device_id})


def add_transaction(from_account, to_account, amount, ts):
    transactions.append({
        "id": next_txn_id(),
        "from_account": from_account,
        "to_account": to_account,
        "amount": round(amount, 2),
        "ts": ts.isoformat(),
    })


def generate_normal_population():
    """Background traffic: ordinary accounts, one device each, sparse random transfers."""
    normal_ids = []
    for i in range(NORMAL_ACCOUNT_COUNT):
        opened_at = BASE_DATE - timedelta(days=random.randint(30, 900))
        account_id = add_account(f"Normal Account {i+1}", opened_at, "NORMAL")
        device_id = add_device(f"device-fingerprint-{account_id}")
        link_device(account_id, device_id)
        normal_ids.append(account_id)

    for _ in range(NORMAL_TRANSACTION_COUNT):
        sender, receiver = random.sample(normal_ids, 2)
        ts = BASE_DATE - timedelta(days=random.randint(0, 180), hours=random.randint(0, 23))
        amount = round(random.uniform(10, 500), 2)
        add_transaction(sender, receiver, amount, ts)

    return normal_ids


def plant_fanin_ring(ring_id="fanin-1", mule_count=15):
    """15 mule accounts all funnel money into 1 cash-out account within a tight window."""
    cash_out = add_account("Cash-Out Account", BASE_DATE - timedelta(days=3), "FRAUD_RING", ring_id)
    link_device(cash_out, add_device(f"device-fingerprint-{cash_out}"))

    window_start = BASE_DATE - timedelta(hours=6)
    for i in range(mule_count):
        opened_at = BASE_DATE - timedelta(days=random.randint(1, 10))
        mule_id = add_account(f"Mule Account {i+1}", opened_at, "FRAUD_RING", ring_id)
        device_id = add_device(f"device-fingerprint-{mule_id}")
        link_device(mule_id, device_id)

        ts = window_start + timedelta(minutes=random.randint(0, 360))
        amount = round(random.uniform(180, 220), 2)
        add_transaction(mule_id, cash_out, amount, ts)

    return cash_out


def plant_cycle_ring(ring_id="cycle-1"):
    """A -> B -> C -> A: money laundering through a closed loop."""
    opened_at = BASE_DATE - timedelta(days=20)
    a = add_account("Cycle Account A", opened_at, "FRAUD_RING", ring_id)
    b = add_account("Cycle Account B", opened_at, "FRAUD_RING", ring_id)
    c = add_account("Cycle Account C", opened_at, "FRAUD_RING", ring_id)

    for acct in (a, b, c):
        device_id = add_device(f"device-fingerprint-{acct}")
        link_device(acct, device_id)

    start = BASE_DATE - timedelta(hours=12)
    amount = 5000.00
    add_transaction(a, b, amount, start)
    add_transaction(b, c, amount * 0.98, start + timedelta(hours=2))
    add_transaction(c, a, amount * 0.95, start + timedelta(hours=5))

    return (a, b, c)


def plant_device_cluster_ring(ring_id="devcluster-1", member_count=5):
    """5 'new' accounts, all opened within days of each other, all sharing one device."""
    shared_device = add_device("shared-device-fraud-cluster")
    members = []
    base_open = BASE_DATE - timedelta(days=6)
    for i in range(member_count):
        opened_at = base_open + timedelta(days=random.randint(0, 4))
        account_id = add_account(f"Synthetic Identity {i+1}", opened_at, "FRAUD_RING", ring_id)
        link_device(account_id, shared_device)
        members.append(account_id)

    # light transaction traffic between cluster members to make it a connected component
    for i in range(len(members) - 1):
        ts = base_open + timedelta(days=i, hours=random.randint(0, 23))
        add_transaction(members[i], members[i + 1], round(random.uniform(50, 150), 2), ts)

    return members


def plant_family_lookalike(ring_id="lookalike-family-1", member_count=4):
    """A family sharing one household device — looks like a device cluster but is innocent.
    Opened months apart (not days), normal occasional transfers, no tight time window."""
    shared_device = add_device("shared-device-family-household")
    members = []
    for i in range(member_count):
        opened_at = BASE_DATE - timedelta(days=random.randint(60, 720))
        account_id = add_account(f"Family Member {i+1}", opened_at, "LOOKALIKE", ring_id)
        link_device(account_id, shared_device)
        members.append(account_id)

    for _ in range(6):
        sender, receiver = random.sample(members, 2)
        ts = BASE_DATE - timedelta(days=random.randint(0, 300), hours=random.randint(0, 23))
        add_transaction(sender, receiver, round(random.uniform(20, 300), 2), ts)

    return members


def plant_business_lookalike(ring_id="lookalike-business-1", member_count=6):
    """A small business: owner + employees sharing one device/IP — looks like a
    device cluster but has normal, spread-out merchant-style transaction patterns."""
    shared_device = add_device("shared-device-small-business")
    members = []
    for i in range(member_count):
        opened_at = BASE_DATE - timedelta(days=random.randint(200, 800))
        account_id = add_account(f"Business Account {i+1}", opened_at, "LOOKALIKE", ring_id)
        link_device(account_id, shared_device)
        members.append(account_id)

    for _ in range(20):
        sender, receiver = random.sample(members, 2)
        ts = BASE_DATE - timedelta(days=random.randint(0, 400), hours=random.randint(0, 23))
        add_transaction(sender, receiver, round(random.uniform(15, 250), 2), ts)

    return members


def write_csv(filename, rows, fieldnames):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {len(rows):5d} rows -> {path}")


def main():
    generate_normal_population()
    fanin_cash_out = plant_fanin_ring()
    cycle_members = plant_cycle_ring()
    devcluster_members = plant_device_cluster_ring()
    family_members = plant_family_lookalike()
    business_members = plant_business_lookalike()

    write_csv("accounts.csv", accounts, ["id", "name", "opened_at", "label", "ring_id"])
    write_csv("devices.csv", devices, ["id", "fingerprint"])
    write_csv("account_device.csv", account_device, ["account_id", "device_id"])
    write_csv("transactions.csv", transactions, ["id", "from_account", "to_account", "amount", "ts"])

    print()
    print("Ground truth for verification:")
    print(f"  fan-in cash-out account:   {fanin_cash_out}")
    print(f"  cycle A->B->C->A accounts: {cycle_members}")
    print(f"  device-cluster accounts:   {devcluster_members}")
    print(f"  family look-alike:         {family_members}")
    print(f"  business look-alike:       {business_members}")
    print(f"  total accounts: {len(accounts)}, devices: {len(devices)}, transactions: {len(transactions)}")


if __name__ == "__main__":
    main()
