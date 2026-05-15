# Lab 2 — Shell 워밍업: Bash와 PowerShell로 같은 문제 풀기

## 목표

같은 문제를 **Bash**와 **PowerShell** 양쪽으로 풀어보며, 텍스트 vs 객체 파이프라인의 차이를 체감한다.

## 사용할 샘플 데이터

```bash
# Bash (WSL)
cat > /tmp/employees.csv <<'EOF'
id,name,department,salary,joined
1,Alice,Engineering,8500,2020-03-15
2,Bob,Sales,6200,2019-07-01
3,Charlie,Engineering,9700,2018-01-20
4,Diana,HR,5800,2021-11-09
5,Eve,Engineering,7100,2022-05-30
6,Frank,Sales,7400,2020-09-12
7,Grace,HR,6300,2023-02-14
8,Henry,Engineering,11000,2017-06-25
EOF
```

```powershell
# PowerShell
@'
id,name,department,salary,joined
1,Alice,Engineering,8500,2020-03-15
2,Bob,Sales,6200,2019-07-01
3,Charlie,Engineering,9700,2018-01-20
4,Diana,HR,5800,2021-11-09
5,Eve,Engineering,7100,2022-05-30
6,Frank,Sales,7400,2020-09-12
7,Grace,HR,6300,2023-02-14
8,Henry,Engineering,11000,2017-06-25
'@ | Set-Content $env:TEMP\employees.csv
```

---

## 문제 1: Engineering 부서 사원 이름만 출력

### Bash

```bash
awk -F, '$3=="Engineering" {print $2}' /tmp/employees.csv
```

또는:

```bash
grep ',Engineering,' /tmp/employees.csv | cut -d, -f2
```

### PowerShell

```powershell
Import-Csv $env:TEMP\employees.csv |
    Where-Object department -eq 'Engineering' |
    Select-Object -ExpandProperty name
```

---

## 문제 2: 부서별 평균 연봉

### Bash

```bash
awk -F, 'NR>1 {sum[$3]+=$4; count[$3]++} END {for (d in sum) printf "%-15s %.0f\n", d, sum[d]/count[d]}' /tmp/employees.csv
```

### PowerShell

```powershell
Import-Csv $env:TEMP\employees.csv |
    Group-Object department |
    ForEach-Object {
        [PSCustomObject]@{
            Department = $_.Name
            AvgSalary  = [math]::Round(($_.Group | Measure-Object salary -Average).Average, 0)
        }
    } | Sort AvgSalary -Descending
```

> **체감 포인트**: Bash는 `awk`의 연관배열 트릭이 필요. PowerShell은 객체이므로 `Group-Object`로 직관적.

---

## 문제 3: 입사일 기준 가장 오래된 3명

### Bash

```bash
tail -n +2 /tmp/employees.csv | sort -t, -k5 | head -3
```

### PowerShell

```powershell
Import-Csv $env:TEMP\employees.csv |
    Sort-Object { [datetime]$_.joined } |
    Select-Object -First 3 name, department, joined
```

---

## 문제 4: 연봉 7000 이상 사원을 JSON으로

### Bash

```bash
# jq 활용
awk -F, 'NR==1 {next} $4>=7000 {printf "{\"name\":\"%s\",\"salary\":%s}\n", $2, $4}' /tmp/employees.csv | jq -s '.'
```

### PowerShell

```powershell
Import-Csv $env:TEMP\employees.csv |
    Where-Object { [int]$_.salary -ge 7000 } |
    Select-Object name, @{N='salary';E={[int]$_.salary}} |
    ConvertTo-Json
```

> **체감**: Bash는 `awk` + `jq`로 두 단계. PowerShell은 객체 변환이 cmdlet 하나로 끝.

---

## 문제 5: 부서별 인원수 (간단)

### Bash

```bash
tail -n +2 /tmp/employees.csv | cut -d, -f3 | sort | uniq -c | sort -rn
```

### PowerShell

```powershell
Import-Csv $env:TEMP\employees.csv |
    Group-Object department |
    Sort-Object Count -Descending |
    Select-Object Count, Name
```

---

## 문제 6 (보너스): 동시 작업 — Bash와 PS를 함께

WSL에서 Bash로 데이터를 생성하고, PowerShell에서 분석:

```bash
# WSL Bash
seq 1 1000 | awk 'BEGIN{srand()} {printf "%d,%d\n", $1, int(rand()*10000)}' > /tmp/data.csv
```

```powershell
# Windows PowerShell — WSL 경로 접근
Get-Content '\\wsl$\Ubuntu-22.04\tmp\data.csv' |
    ForEach-Object {
        $parts = $_ -split ','
        [PSCustomObject]@{
            Id = [int]$parts[0]
            Value = [int]$parts[1]
        }
    } |
    Measure-Object Value -Sum -Average -Maximum -Minimum
```

---

## 채점 가이드

| 기준 | 통과 조건 |
|---|---|
| 문제 1~5를 양쪽 셸로 직접 입력해서 같은 결과를 얻었는가? | 5문제 × 2언어 = 10개 답 확인 |
| 텍스트 vs 객체 차이를 한 문제 골라 동료에게 설명할 수 있는가? | 문제 4가 가장 차이가 큼 |
| 보너스: WSL ↔ Windows 데이터 공유 경험 | `\\wsl$\` 또는 `/mnt/c/` 사용 |

---

## 회고 질문

1. 어느 쪽이 더 빨리 풀렸는가? **(상황에 따라 답이 다르다 — 일회성 텍스트 작업은 Bash, 구조화 데이터는 PowerShell이 보통 우세)**
2. PowerShell의 `Import-Csv`처럼 Bash에서 비슷한 추상화를 만들려면? (jq, Miller `mlr`, csvkit)
3. 만약 1억 행의 CSV였다면 어느 쪽이 더 빠를까? **(보통 awk/Miller 같은 스트리밍 도구가 우세. PS는 객체 변환 비용이 큼)**
4. 본인 업무에서 Bash와 PowerShell 중 어느 쪽이 더 가치 있는가?

다음: [`../checklist.md`](../checklist.md)
