param(
    [int]$Port = 55432,
    [string]$BaseDir = (Join-Path $PSScriptRoot 'data\local-postgresql'),
    [PSCredential]$Credential = $null,
    [string]$Database = 'datingapp',
    [int]$StartupTimeoutSeconds = 10,
    [string]$BackupSchema = '',
    [switch]$ProfileDataOnly,
    [switch]$ThrowOnFailure
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptRoot = Split-Path -Parent $PSCommandPath
if (-not [System.IO.Path]::IsPathRooted($BaseDir)) {
    $BaseDir = Join-Path $ScriptRoot $BaseDir
}

function Exit-OrThrow {
    param(
        [int]$ExitCode,
        [string]$Message
    )

    $global:LASTEXITCODE = $ExitCode
    if ($ThrowOnFailure) {
        throw $Message
    }

    exit $ExitCode
}

function New-DefaultCredential {
    $securePassword = ConvertTo-SecureString -String 'datingapp' -AsPlainText -Force
    return [PSCredential]::new('datingapp', $securePassword)
}

function Assert-SafeSqlName {
    param(
        [string]$Name,
        [string]$Label
    )

    $trimmed = $Name.Trim()
    if ($trimmed -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw ('{0} must match ^[A-Za-z_][A-Za-z0-9_]*$: {1}' -f $Label, $Name)
    }

    return $trimmed
}

function Resolve-BackupSchemaName {
    param([string]$RequestedName)

    $resolved = if ([string]::IsNullOrWhiteSpace($RequestedName)) {
        'reset_backup_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
    }
    else {
        $RequestedName
    }

    $validated = Assert-SafeSqlName -Name $resolved -Label 'Backup schema name'
    if ($validated -in @('public', 'pg_catalog', 'information_schema')) {
        throw "Backup schema name is reserved and cannot be used: $validated"
    }

    return $validated
}

function Quote-SqlIdentifier {
    param([string]$Identifier)

    '"' + $Identifier.Replace('"', '""') + '"'
}

function Get-QualifiedSqlIdentifier {
    param(
        [string]$Schema,
        [string]$Table
    )

    (Quote-SqlIdentifier $Schema) + '.' + (Quote-SqlIdentifier $Table)
}

function Get-QuotedColumnList {
    param([string[]]$Columns)

    ($Columns | ForEach-Object { Quote-SqlIdentifier $_ }) -join ', '
}

function Get-SourceColumnReference {
    param(
        [string]$Column,
        [string]$Alias = 'src'
    )

    "${Alias}." + (Quote-SqlIdentifier $Column)
}

function Get-UpperTrimExpression {
    param([string]$Expression)

    "UPPER(BTRIM($Expression))"
}

function Get-CanonicalFirstExpression {
    param(
        [string]$LeftExpression,
        [string]$RightExpression
    )

    "CASE WHEN CAST($LeftExpression AS TEXT) < CAST($RightExpression AS TEXT) THEN $LeftExpression ELSE $RightExpression END"
}

function Get-CanonicalSecondExpression {
    param(
        [string]$LeftExpression,
        [string]$RightExpression
    )

    "CASE WHEN CAST($LeftExpression AS TEXT) < CAST($RightExpression AS TEXT) THEN $RightExpression ELSE $LeftExpression END"
}

function Get-CanonicalIdExpression {
    param(
        [string]$LeftExpression,
        [string]$RightExpression
    )

    "CASE WHEN CAST($LeftExpression AS TEXT) < CAST($RightExpression AS TEXT) THEN CAST($LeftExpression AS TEXT) || '_' || CAST($RightExpression AS TEXT) ELSE CAST($RightExpression AS TEXT) || '_' || CAST($LeftExpression AS TEXT) END"
}

function Get-CanonicalPairKeyExpression {
    param(
        [string]$LeftExpression,
        [string]$RightExpression
    )

    "CASE WHEN CAST($LeftExpression AS TEXT) <= CAST($RightExpression AS TEXT) THEN CAST($LeftExpression AS TEXT) || '|' || CAST($RightExpression AS TEXT) ELSE CAST($RightExpression AS TEXT) || '|' || CAST($LeftExpression AS TEXT) END"
}

function New-TableSpec {
    param(
        [string]$Name,
        [string[]]$Columns,
        [string]$ImportSelect = '',
        [string]$ImportWhere = '',
        [bool]$UseDistinct = $false
    )

    if ([string]::IsNullOrWhiteSpace($ImportSelect)) {
        $ImportSelect = ($Columns | ForEach-Object { Get-SourceColumnReference -Column $_ }) -join ', '
    }

    [pscustomobject]@{
        Name         = $Name
        Columns      = $Columns
        BackupSelect = Get-QuotedColumnList -Columns $Columns
        ImportSelect = $ImportSelect
        ImportWhere  = $ImportWhere
        UseDistinct  = $UseDistinct
    }
}

function Get-ResetTableSpecs {
    param([bool]$IncludeRelationshipTables)

    $userColumns = @(
        'id',
        'name',
        'bio',
        'birth_date',
        'gender',
        'lat',
        'lon',
        'has_location_set',
        'max_distance_km',
        'min_age',
        'max_age',
        'state',
        'created_at',
        'updated_at',
        'smoking',
        'drinking',
        'wants_kids',
        'looking_for',
        'education',
        'height_cm',
        'db_min_height_cm',
        'db_max_height_cm',
        'db_max_age_diff',
        'email',
        'phone',
        'is_verified',
        'verification_method',
        'verification_code',
        'verification_sent_at',
        'verified_at',
        'pace_messaging_frequency',
        'pace_time_to_first_date',
        'pace_communication_style',
        'pace_depth_preference',
        'deleted_at'
    )

    $profileTables = @(
        (New-TableSpec -Name 'users' -Columns $userColumns -ImportSelect (@(
                (Get-SourceColumnReference -Column 'id'),
                (Get-SourceColumnReference -Column 'name'),
                (Get-SourceColumnReference -Column 'bio'),
                (Get-SourceColumnReference -Column 'birth_date'),
                (Get-SourceColumnReference -Column 'gender'),
                (Get-SourceColumnReference -Column 'lat'),
                (Get-SourceColumnReference -Column 'lon'),
                (Get-SourceColumnReference -Column 'has_location_set'),
                (Get-SourceColumnReference -Column 'max_distance_km'),
                (Get-SourceColumnReference -Column 'min_age'),
                (Get-SourceColumnReference -Column 'max_age'),
                (Get-SourceColumnReference -Column 'state'),
                (Get-SourceColumnReference -Column 'created_at'),
                (Get-SourceColumnReference -Column 'updated_at'),
                (Get-SourceColumnReference -Column 'smoking'),
                (Get-SourceColumnReference -Column 'drinking'),
                (Get-SourceColumnReference -Column 'wants_kids'),
                (Get-SourceColumnReference -Column 'looking_for'),
                (Get-SourceColumnReference -Column 'education'),
                (Get-SourceColumnReference -Column 'height_cm'),
                (Get-SourceColumnReference -Column 'db_min_height_cm'),
                (Get-SourceColumnReference -Column 'db_max_height_cm'),
                (Get-SourceColumnReference -Column 'db_max_age_diff'),
                (Get-SourceColumnReference -Column 'email'),
                (Get-SourceColumnReference -Column 'phone'),
                (Get-SourceColumnReference -Column 'is_verified'),
                (Get-SourceColumnReference -Column 'verification_method'),
                'NULL',
                'NULL',
                (Get-SourceColumnReference -Column 'verified_at'),
                (Get-SourceColumnReference -Column 'pace_messaging_frequency'),
                (Get-SourceColumnReference -Column 'pace_time_to_first_date'),
                (Get-SourceColumnReference -Column 'pace_communication_style'),
                (Get-SourceColumnReference -Column 'pace_depth_preference'),
                (Get-SourceColumnReference -Column 'deleted_at')
            ) -join ', ')),
        (New-TableSpec -Name 'user_photos' -Columns @('user_id', 'position', 'url', 'created_at')),
        (New-TableSpec -Name 'user_interests' -Columns @('user_id', 'interest'))
    )

    $interestedInValue = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'gender')
    $smokingValue = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'value')
    $drinkingValue = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'value')
    $lookingForValue = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'value')
    $educationValue = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'value')
    $kidsCanonical = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'value')
    $kidsValue = "CASE WHEN $kidsCanonical = 'YES' THEN 'SOMEDAY' WHEN $kidsCanonical = 'OPEN_TO_IT' THEN 'OPEN' ELSE $kidsCanonical END"

    $profileTables += @(
        (New-TableSpec -Name 'user_interested_in' -Columns @('user_id', 'gender') -UseDistinct $true -ImportSelect (@(
                (Get-SourceColumnReference -Column 'user_id'),
                $interestedInValue
            ) -join ', ') -ImportWhere "WHERE $interestedInValue IN ('MALE', 'FEMALE', 'OTHER')"),
        (New-TableSpec -Name 'user_db_smoking' -Columns @('user_id', 'value') -UseDistinct $true -ImportSelect (@(
                (Get-SourceColumnReference -Column 'user_id'),
                $smokingValue
            ) -join ', ') -ImportWhere "WHERE $smokingValue IN ('NEVER', 'SOMETIMES', 'REGULARLY')"),
        (New-TableSpec -Name 'user_db_drinking' -Columns @('user_id', 'value') -UseDistinct $true -ImportSelect (@(
                (Get-SourceColumnReference -Column 'user_id'),
                $drinkingValue
            ) -join ', ') -ImportWhere "WHERE $drinkingValue IN ('NEVER', 'SOCIALLY', 'REGULARLY')"),
        (New-TableSpec -Name 'user_db_wants_kids' -Columns @('user_id', 'value') -UseDistinct $true -ImportSelect (@(
                (Get-SourceColumnReference -Column 'user_id'),
                $kidsValue
            ) -join ', ') -ImportWhere "WHERE $kidsValue IN ('NO', 'OPEN', 'SOMEDAY', 'HAS_KIDS')"),
        (New-TableSpec -Name 'user_db_looking_for' -Columns @('user_id', 'value') -UseDistinct $true -ImportSelect (@(
                (Get-SourceColumnReference -Column 'user_id'),
                $lookingForValue
            ) -join ', ') -ImportWhere "WHERE $lookingForValue IN ('CASUAL', 'SHORT_TERM', 'LONG_TERM', 'MARRIAGE', 'UNSURE')"),
        (New-TableSpec -Name 'user_db_education' -Columns @('user_id', 'value') -UseDistinct $true -ImportSelect (@(
                (Get-SourceColumnReference -Column 'user_id'),
                $educationValue
            ) -join ', ') -ImportWhere "WHERE $educationValue IN ('HIGH_SCHOOL', 'SOME_COLLEGE', 'BACHELORS', 'MASTERS', 'PHD', 'TRADE_SCHOOL', 'OTHER')"),
        (New-TableSpec -Name 'profile_notes' -Columns @('author_id', 'subject_id', 'content', 'created_at', 'updated_at', 'deleted_at') -ImportWhere "WHERE src.`"deleted_at`" IS NULL AND src.`"author_id`" <> src.`"subject_id`" AND BTRIM(src.`"content`") <> ''")
    )

    if (-not $IncludeRelationshipTables) {
        return $profileTables
    }

    $matchState = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'state')
    $matchEndReason = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'end_reason')
    $matchUserA = Get-CanonicalFirstExpression -LeftExpression (Get-SourceColumnReference -Column 'user_a') -RightExpression (Get-SourceColumnReference -Column 'user_b')
    $matchUserB = Get-CanonicalSecondExpression -LeftExpression (Get-SourceColumnReference -Column 'user_a') -RightExpression (Get-SourceColumnReference -Column 'user_b')
    $matchId = Get-CanonicalIdExpression -LeftExpression (Get-SourceColumnReference -Column 'user_a') -RightExpression (Get-SourceColumnReference -Column 'user_b')

    $conversationReasonA = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'archive_reason_a')
    $conversationReasonB = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'archive_reason_b')
    $conversationUserA = Get-CanonicalFirstExpression -LeftExpression (Get-SourceColumnReference -Column 'user_a') -RightExpression (Get-SourceColumnReference -Column 'user_b')
    $conversationUserB = Get-CanonicalSecondExpression -LeftExpression (Get-SourceColumnReference -Column 'user_a') -RightExpression (Get-SourceColumnReference -Column 'user_b')
    $conversationId = Get-CanonicalIdExpression -LeftExpression (Get-SourceColumnReference -Column 'user_a') -RightExpression (Get-SourceColumnReference -Column 'user_b')

    $likeDirection = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'direction')
    $friendRequestStatus = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'status')
    $friendRequestPairKey = Get-CanonicalPairKeyExpression -LeftExpression (Get-SourceColumnReference -Column 'from_user_id') -RightExpression (Get-SourceColumnReference -Column 'to_user_id')
    $friendRequestPendingMarker = "CASE WHEN $friendRequestStatus = 'PENDING' THEN 'PENDING' ELSE NULL END"
    $reportReason = Get-UpperTrimExpression -Expression (Get-SourceColumnReference -Column 'reason')

    $relationshipTables = @(
        (New-TableSpec -Name 'likes' -Columns @('id', 'who_likes', 'who_got_liked', 'direction', 'created_at', 'deleted_at') -ImportSelect (@(
                (Get-SourceColumnReference -Column 'id'),
                (Get-SourceColumnReference -Column 'who_likes'),
                (Get-SourceColumnReference -Column 'who_got_liked'),
                $likeDirection,
                (Get-SourceColumnReference -Column 'created_at'),
                (Get-SourceColumnReference -Column 'deleted_at')
            ) -join ', ') -ImportWhere "WHERE src.`"deleted_at`" IS NULL AND src.`"who_likes`" <> src.`"who_got_liked`" AND $likeDirection IN ('LIKE', 'SUPER_LIKE', 'PASS')"),
        (New-TableSpec -Name 'matches' -Columns @('id', 'user_a', 'user_b', 'created_at', 'updated_at', 'state', 'ended_at', 'ended_by', 'end_reason', 'deleted_at') -ImportSelect (@(
                $matchId,
                $matchUserA,
                $matchUserB,
                (Get-SourceColumnReference -Column 'created_at'),
                (Get-SourceColumnReference -Column 'updated_at'),
                $matchState,
                (Get-SourceColumnReference -Column 'ended_at'),
                (Get-SourceColumnReference -Column 'ended_by'),
                "CASE WHEN src.`"end_reason`" IS NULL THEN NULL ELSE $matchEndReason END",
                (Get-SourceColumnReference -Column 'deleted_at')
            ) -join ', ') -ImportWhere "WHERE src.`"deleted_at`" IS NULL AND src.`"user_a`" <> src.`"user_b`" AND $matchState IN ('ACTIVE', 'FRIENDS', 'UNMATCHED', 'GRACEFUL_EXIT', 'BLOCKED') AND (src.`"end_reason`" IS NULL OR $matchEndReason IN ('FRIEND_ZONE', 'GRACEFUL_EXIT', 'UNMATCH', 'BLOCK')) AND (src.`"ended_by`" IS NULL OR src.`"ended_by`" = src.`"user_a`" OR src.`"ended_by`" = src.`"user_b`")"),
        (New-TableSpec -Name 'friend_requests' -Columns @('id', 'from_user_id', 'to_user_id', 'created_at', 'status', 'responded_at', 'pair_key', 'pending_marker') -ImportSelect (@(
                (Get-SourceColumnReference -Column 'id'),
                (Get-SourceColumnReference -Column 'from_user_id'),
                (Get-SourceColumnReference -Column 'to_user_id'),
                (Get-SourceColumnReference -Column 'created_at'),
                $friendRequestStatus,
                (Get-SourceColumnReference -Column 'responded_at'),
                $friendRequestPairKey,
                $friendRequestPendingMarker
            ) -join ', ') -ImportWhere "WHERE src.`"from_user_id`" <> src.`"to_user_id`" AND $friendRequestStatus IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')"),
        (New-TableSpec -Name 'conversations' -Columns @('id', 'user_a', 'user_b', 'created_at', 'last_message_at', 'user_a_last_read_at', 'user_b_last_read_at', 'archived_at_a', 'archive_reason_a', 'archived_at_b', 'archive_reason_b', 'visible_to_user_a', 'visible_to_user_b', 'deleted_at') -ImportSelect (@(
                $conversationId,
                $conversationUserA,
                $conversationUserB,
                (Get-SourceColumnReference -Column 'created_at'),
                (Get-SourceColumnReference -Column 'last_message_at'),
                (Get-SourceColumnReference -Column 'user_a_last_read_at'),
                (Get-SourceColumnReference -Column 'user_b_last_read_at'),
                (Get-SourceColumnReference -Column 'archived_at_a'),
                "CASE WHEN src.`"archive_reason_a`" IS NULL THEN NULL ELSE $conversationReasonA END",
                (Get-SourceColumnReference -Column 'archived_at_b'),
                "CASE WHEN src.`"archive_reason_b`" IS NULL THEN NULL ELSE $conversationReasonB END",
                (Get-SourceColumnReference -Column 'visible_to_user_a'),
                (Get-SourceColumnReference -Column 'visible_to_user_b'),
                (Get-SourceColumnReference -Column 'deleted_at')
            ) -join ', ') -ImportWhere "WHERE src.`"deleted_at`" IS NULL AND src.`"user_a`" <> src.`"user_b`" AND (src.`"archive_reason_a`" IS NULL OR $conversationReasonA IN ('FRIEND_ZONE', 'GRACEFUL_EXIT', 'UNMATCH', 'BLOCK')) AND (src.`"archive_reason_b`" IS NULL OR $conversationReasonB IN ('FRIEND_ZONE', 'GRACEFUL_EXIT', 'UNMATCH', 'BLOCK'))"),
        (New-TableSpec -Name 'messages' -Columns @('id', 'conversation_id', 'sender_id', 'content', 'created_at', 'deleted_at')),
        (New-TableSpec -Name 'blocks' -Columns @('id', 'blocker_id', 'blocked_id', 'created_at', 'deleted_at') -ImportWhere 'WHERE src."deleted_at" IS NULL AND src."blocker_id" <> src."blocked_id"'),
        (New-TableSpec -Name 'reports' -Columns @('id', 'reporter_id', 'reported_user_id', 'reason', 'description', 'created_at', 'deleted_at') -ImportSelect (@(
                (Get-SourceColumnReference -Column 'id'),
                (Get-SourceColumnReference -Column 'reporter_id'),
                (Get-SourceColumnReference -Column 'reported_user_id'),
                $reportReason,
                (Get-SourceColumnReference -Column 'description'),
                (Get-SourceColumnReference -Column 'created_at'),
                (Get-SourceColumnReference -Column 'deleted_at')
            ) -join ', ') -ImportWhere "WHERE src.`"deleted_at`" IS NULL AND src.`"reporter_id`" <> src.`"reported_user_id`" AND $reportReason IN ('SPAM', 'INAPPROPRIATE_CONTENT', 'HARASSMENT', 'FAKE_PROFILE', 'UNDERAGE', 'OTHER')")
    )

    return $profileTables + $relationshipTables
}

function New-BackupSql {
    param(
        [string]$BackupSchemaName,
        [object[]]$TableSpecs
    )

    $lines = @(
        "CREATE SCHEMA IF NOT EXISTS $(Quote-SqlIdentifier $BackupSchemaName);",
        "SET TIME ZONE 'UTC';",
        'BEGIN;'
    )

    foreach ($spec in $TableSpecs) {
        $qualifiedBackupTable = Get-QualifiedSqlIdentifier -Schema $BackupSchemaName -Table $spec.Name
        $qualifiedPublicTable = Get-QualifiedSqlIdentifier -Schema 'public' -Table $spec.Name
        $lines += "DROP TABLE IF EXISTS $qualifiedBackupTable;"
        $lines += "CREATE TABLE $qualifiedBackupTable AS SELECT $($spec.BackupSelect) FROM $qualifiedPublicTable;"
    }

    $lines += 'COMMIT;'
    return $lines
}

function New-ResetSchemaSql {
    @(
        "SET TIME ZONE 'UTC';",
        'BEGIN;',
        'DROP SCHEMA public CASCADE;',
        'CREATE SCHEMA public;',
        'COMMIT;'
    )
}

function New-ImportSql {
    param(
        [string]$BackupSchemaName,
        [object[]]$TableSpecs
    )

    $lines = @(
        "SET TIME ZONE 'UTC';",
        'BEGIN;'
    )

    foreach ($spec in $TableSpecs) {
        if ($spec.Name -eq 'messages') {
            $conversationIdExpr = Get-CanonicalIdExpression -LeftExpression (Get-SourceColumnReference -Alias 'conv' -Column 'user_a') -RightExpression (Get-SourceColumnReference -Alias 'conv' -Column 'user_b')
            $messageColumns = Get-QuotedColumnList -Columns $spec.Columns
            $backupMessages = Get-QualifiedSqlIdentifier -Schema $BackupSchemaName -Table 'messages'
            $backupConversations = Get-QualifiedSqlIdentifier -Schema $BackupSchemaName -Table 'conversations'
            $lines += @(
                "INSERT INTO public.messages ($messageColumns)",
                'SELECT msg."id",',
                "       $conversationIdExpr,",
                '       msg."sender_id",',
                '       msg."content",',
                '       msg."created_at",',
                '       msg."deleted_at"',
                "FROM $backupMessages msg",
                "JOIN $backupConversations conv ON conv.`"id`" = msg.`"conversation_id`"",
                'WHERE conv."deleted_at" IS NULL',
                '  AND conv."user_a" <> conv."user_b"',
                "  AND BTRIM(msg.`"content`") <> ''",
                '  AND msg."deleted_at" IS NULL;'
            )
            continue
        }

        $targetTable = Get-QualifiedSqlIdentifier -Schema 'public' -Table $spec.Name
        $sourceTable = Get-QualifiedSqlIdentifier -Schema $BackupSchemaName -Table $spec.Name
        $columnsSql = Get-QuotedColumnList -Columns $spec.Columns
        $selectPrefix = if ($spec.UseDistinct) { 'SELECT DISTINCT' } else { 'SELECT' }
        $statement = "INSERT INTO $targetTable ($columnsSql) $selectPrefix $($spec.ImportSelect) FROM $sourceTable src"
        if (-not [string]::IsNullOrWhiteSpace($spec.ImportWhere)) {
            $statement += " $($spec.ImportWhere)"
        }
        $lines += "$statement;"
    }

    $lines += 'COMMIT;'
    return $lines
}

function Invoke-StartLocalPostgres {
    param(
        [int]$TargetPort,
        [string]$TargetBaseDir,
        [PSCredential]$TargetCredential,
        [string]$TargetDatabase,
        [int]$TargetStartupTimeoutSeconds
    )

    try {
        $null = & (Join-Path $ScriptRoot 'start_local_postgres.ps1') `
            -Port $TargetPort `
            -BaseDir $TargetBaseDir `
            -Superuser $TargetCredential.UserName `
            -Credential $TargetCredential `
            -Database $TargetDatabase `
            -StartupTimeoutSeconds $TargetStartupTimeoutSeconds
        return [int]$LASTEXITCODE
    }
    catch {
        if ($LASTEXITCODE -ne 0) {
            return [int]$LASTEXITCODE
        }
        return 1
    }
}

function Invoke-PsqlFile {
    param(
        [string]$SqlFile,
        [int]$TargetPort,
        [string]$TargetUsername,
        [string]$TargetPassword,
        [string]$TargetDatabase
    )

    $previousPassword = $env:PGPASSWORD
    $env:PGPASSWORD = $TargetPassword

    try {
        $psqlArgs = @(
            '-h',
            'localhost',
            '-p',
            $TargetPort.ToString(),
            '-U',
            $TargetUsername,
            '-d',
            $TargetDatabase,
            '-w',
            '-X',
            '-v',
            'ON_ERROR_STOP=1',
            '-f',
            $SqlFile
        )
        $null = & psql @psqlArgs
        return [int]$LASTEXITCODE
    }
    catch {
        if ($LASTEXITCODE -ne 0) {
            return [int]$LASTEXITCODE
        }
        return 1
    }
    finally {
        if ($null -eq $previousPassword) {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }
        else {
            $env:PGPASSWORD = $previousPassword
        }
    }
}

function Invoke-MavenBootstrap {
    param(
        [string]$JdbcUrl,
        [string]$TargetUsername,
        [string]$TargetPassword
    )

    $previousDialect = $env:DATING_APP_DB_DIALECT
    $previousUrl = $env:DATING_APP_DB_URL
    $previousUsername = $env:DATING_APP_DB_USERNAME
    $previousPassword = $env:DATING_APP_DB_PASSWORD

    $env:DATING_APP_DB_DIALECT = 'POSTGRESQL'
    $env:DATING_APP_DB_URL = $JdbcUrl
    $env:DATING_APP_DB_USERNAME = $TargetUsername
    $env:DATING_APP_DB_PASSWORD = $TargetPassword

    try {
        Push-Location $ScriptRoot
        $mavenArgs = @(
            '-Dcheckstyle.skip=true',
            '-Dtest=PostgresqlSchemaBootstrapSmokeTest',
            'test'
        )
        $null = & mvn @mavenArgs
        return [int]$LASTEXITCODE
    }
    catch {
        if ($LASTEXITCODE -ne 0) {
            return [int]$LASTEXITCODE
        }
        return 1
    }
    finally {
        Pop-Location

        if ($null -eq $previousDialect) {
            Remove-Item Env:DATING_APP_DB_DIALECT -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_DIALECT = $previousDialect
        }

        if ($null -eq $previousUrl) {
            Remove-Item Env:DATING_APP_DB_URL -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_URL = $previousUrl
        }

        if ($null -eq $previousUsername) {
            Remove-Item Env:DATING_APP_DB_USERNAME -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_USERNAME = $previousUsername
        }

        if ($null -eq $previousPassword) {
            Remove-Item Env:DATING_APP_DB_PASSWORD -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_PASSWORD = $previousPassword
        }
    }
}

function Invoke-SmokeValidation {
    param(
        [int]$TargetPort,
        [PSCredential]$TargetCredential,
        [string]$TargetDatabase
    )

    try {
        $null = & (Join-Path $ScriptRoot 'run_postgresql_smoke.ps1') `
            -Port $TargetPort `
            -Username $TargetCredential.UserName `
            -Credential $TargetCredential `
            -Database $TargetDatabase `
            -ThrowOnFailure
        return [int]$LASTEXITCODE
    }
    catch {
        if ($LASTEXITCODE -ne 0) {
            return [int]$LASTEXITCODE
        }
        return 1
    }
}

function Format-FailureMessage {
    param(
        [string]$Message,
        [bool]$BackupSnapshotCreated,
        [string]$ResolvedBackupSchema,
        [string]$TempDirectory,
        [bool]$KeepTempDirectory
    )

    $parts = @($Message)
    if ($BackupSnapshotCreated) {
        $parts += "Backup schema preserved as $ResolvedBackupSchema."
    }
    if ($KeepTempDirectory -and -not [string]::IsNullOrWhiteSpace($TempDirectory)) {
        $parts += "Temporary SQL files were kept at $TempDirectory."
    }
    return ($parts -join ' ')
}

if ($StartupTimeoutSeconds -lt 1) {
    throw 'StartupTimeoutSeconds must be at least 1 second.'
}

if ($null -eq $Credential) {
    $Credential = New-DefaultCredential
}

$resolvedDatabase = Assert-SafeSqlName -Name $Database -Label 'Database name'
$resolvedBackupSchema = Resolve-BackupSchemaName -RequestedName $BackupSchema
$plainPassword = $Credential.GetNetworkCredential().Password
$jdbcUrl = "jdbc:postgresql://localhost:$Port/$resolvedDatabase"

$tableSpecs = Get-ResetTableSpecs -IncludeRelationshipTables (-not $ProfileDataOnly.IsPresent)
$tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("reset-local-postgres-" + [System.Guid]::NewGuid())
$backupSqlFile = Join-Path $tempDirectory 'backup.sql'
$resetSqlFile = Join-Path $tempDirectory 'reset.sql'
$importSqlFile = Join-Path $tempDirectory 'import.sql'

$backupSnapshotCreated = $false
$failureExitCode = 0
$failureMessage = $null
$keepTempDirectory = $false

try {
    $startExitCode = Invoke-StartLocalPostgres `
        -TargetPort $Port `
        -TargetBaseDir $BaseDir `
        -TargetCredential $Credential `
        -TargetDatabase $resolvedDatabase `
        -TargetStartupTimeoutSeconds $StartupTimeoutSeconds
    if ($startExitCode -ne 0) {
        $failureExitCode = $startExitCode
        $failureMessage = "Local PostgreSQL startup failed with exit code $startExitCode."
    }

    if ($failureExitCode -eq 0) {
        New-Item -ItemType Directory -Path $tempDirectory | Out-Null
        Set-Content -Path $backupSqlFile -Value (New-BackupSql -BackupSchemaName $resolvedBackupSchema -TableSpecs $tableSpecs) -Encoding UTF8
        Set-Content -Path $resetSqlFile -Value (New-ResetSchemaSql) -Encoding UTF8
        Set-Content -Path $importSqlFile -Value (New-ImportSql -BackupSchemaName $resolvedBackupSchema -TableSpecs $tableSpecs) -Encoding UTF8

        $backupExitCode = Invoke-PsqlFile `
            -SqlFile $backupSqlFile `
            -TargetPort $Port `
            -TargetUsername $Credential.UserName `
            -TargetPassword $plainPassword `
            -TargetDatabase $resolvedDatabase
        if ($backupExitCode -ne 0) {
            $failureExitCode = $backupExitCode
            $failureMessage = "Backup schema creation failed with exit code $backupExitCode."
            $keepTempDirectory = $true
        }
        else {
            $backupSnapshotCreated = $true
        }
    }

    if ($failureExitCode -eq 0) {
        $resetExitCode = Invoke-PsqlFile `
            -SqlFile $resetSqlFile `
            -TargetPort $Port `
            -TargetUsername $Credential.UserName `
            -TargetPassword $plainPassword `
            -TargetDatabase $resolvedDatabase
        if ($resetExitCode -ne 0) {
            $failureExitCode = $resetExitCode
            $failureMessage = "Public schema reset failed with exit code $resetExitCode."
            $keepTempDirectory = $true
        }
    }

    if ($failureExitCode -eq 0) {
        $bootstrapExitCode = Invoke-MavenBootstrap `
            -JdbcUrl $jdbcUrl `
            -TargetUsername $Credential.UserName `
            -TargetPassword $plainPassword
        if ($bootstrapExitCode -ne 0) {
            $failureExitCode = $bootstrapExitCode
            $failureMessage = "Maven bootstrap failed with exit code $bootstrapExitCode."
            $keepTempDirectory = $true
        }
    }

    if ($failureExitCode -eq 0) {
        $importExitCode = Invoke-PsqlFile `
            -SqlFile $importSqlFile `
            -TargetPort $Port `
            -TargetUsername $Credential.UserName `
            -TargetPassword $plainPassword `
            -TargetDatabase $resolvedDatabase
        if ($importExitCode -ne 0) {
            $failureExitCode = $importExitCode
            $failureMessage = "Fresh-schema import failed with exit code $importExitCode."
            $keepTempDirectory = $true
        }
    }

    if ($failureExitCode -eq 0) {
        $smokeExitCode = Invoke-SmokeValidation `
            -TargetPort $Port `
            -TargetCredential $Credential `
            -TargetDatabase $resolvedDatabase
        if ($smokeExitCode -ne 0) {
            $failureExitCode = $smokeExitCode
            $failureMessage = "PostgreSQL smoke validation failed with exit code $smokeExitCode."
            $keepTempDirectory = $true
        }
    }
}
finally {
    $plainPassword = $null
    if (-not $keepTempDirectory -and (Test-Path $tempDirectory)) {
        Remove-Item -Path $tempDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($failureExitCode -ne 0) {
    $message = Format-FailureMessage `
        -Message $failureMessage `
        -BackupSnapshotCreated $backupSnapshotCreated `
        -ResolvedBackupSchema $resolvedBackupSchema `
        -TempDirectory $tempDirectory `
        -KeepTempDirectory $keepTempDirectory
    Exit-OrThrow -ExitCode $failureExitCode -Message $message
}

$global:LASTEXITCODE = 0
Write-Output 'Local PostgreSQL reset completed successfully.'
Write-Output "Port: $Port"
Write-Output "Database: $resolvedDatabase"
Write-Output "Backup schema: $resolvedBackupSchema"
if ($ProfileDataOnly) {
    Write-Output 'Mode: profile-data-only'
}
